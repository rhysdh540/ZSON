import groovy.json.JsonSlurper
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles
import java.time.ZonedDateTime

plugins {
    java
    idea
    `maven-publish`
    alias(libs.plugins.jmh)
    alias(libs.plugins.grgit)
    alias(libs.plugins.jvmdg)
    alias(libs.plugins.github.release)
}

operator fun String.invoke(): String = rootProject.properties[this] as? String ?: error("Property $this not found")

group = "maven_group"()
base.archivesName = "project_name"()

//region Git
enum class ReleaseChannel(val suffix: String? = null) {
    DEV_BUILD("dev"),
    RELEASE,
}

val headDateTime: ZonedDateTime = grgit.head().dateTime

val branchName = grgit.branch.current().name!!
val releaseTagPrefix = "release/"

val releaseTags = grgit.tag.list()
    .filter { tag -> tag.name.startsWith(releaseTagPrefix) }
    .sortedWith { tag1, tag2 ->
        if (tag1.commit.dateTime != tag2.commit.dateTime)
            tag1.commit.dateTime.compareTo(tag2.commit.dateTime)
        else if (tag1.name.length != tag2.name.length)
            tag1.name.length.compareTo(tag2.name.length)
        else
            tag1.name.compareTo(tag2.name)
    }
    .dropWhile { tag -> tag.commit.dateTime > headDateTime }

val isExternalCI = (rootProject.properties["external_publish"] as String?).toBoolean()
val isRelease = rootProject.hasProperty("release_channel") || isExternalCI
val releaseChannel: ReleaseChannel =
    if (isExternalCI) {
        val tagName = releaseTags.first().name
        val suffix = """-(\w+)\.\d+$""".toRegex().find(tagName)?.groupValues?.get(1)
        if (suffix != null)
            ReleaseChannel.values().find { channel -> channel.suffix == suffix }!!
        else
            ReleaseChannel.RELEASE
    } else {
        if (isRelease)
            ReleaseChannel.valueOf("release_channel"())
        else
            ReleaseChannel.DEV_BUILD
    }

println("Release Channel: $releaseChannel")

val minorVersion = "project_version"()
val minorTagPrefix = "${releaseTagPrefix}${minorVersion}."

val patchHistory = releaseTags
    .map { tag -> tag.name }
    .filter { name -> name.startsWith(minorTagPrefix) }
    .map { name -> name.substring(minorTagPrefix.length) }

val maxPatch = patchHistory.maxOfOrNull { it.substringBefore('-').toInt() }
val patch = if (maxPatch == null) 0
                else if (patchHistory.contains(maxPatch.toString()) && !isExternalCI)
                    maxPatch + 1
                else maxPatch
var patchAndSuffix = patch.toString()

if (releaseChannel.suffix != null) {
    patchAndSuffix += "-${releaseChannel.suffix}"
}

val versionString = "${minorVersion}.${patchAndSuffix}"
val versionTagName = "${releaseTagPrefix}${versionString}"

//endregion

version = versionString
println("ZSON Version: $versionString")

data class DowngradeInfo(val displayName: String, val version: JavaVersion, val testVersion: JavaVersion)

@Suppress("UNCHECKED_CAST")
val allJavaVersions: List<DowngradeInfo> =
    (JsonSlurper().parseText("supported_java_versions"()) as List<Map<String, String>>)
        .map { map ->
            val version = map["version"]!! // main version to downgrade to
            val displayName = map["display"] ?: version // display name
            val test = map["test"] ?: version // what to downgrade tests to
            DowngradeInfo(displayName, JavaVersion.toVersion(version), JavaVersion.toVersion(test))
        }
        .sortedByDescending { it.version.majorVersion.toInt() }
        .toList()

println("Java Versions: ${allJavaVersions.joinToString { it.displayName }}")

val currentJavaVersion: JavaVersion = allJavaVersions.first().version

val downgradingJavaVersions = allJavaVersions.filter { it.version.majorVersion.toInt() < currentJavaVersion.majorVersion.toInt() }

java {
    toolchain {
        languageVersion.set(currentJavaVersion.toLanguageVersion())
    }

    withSourcesJar()
    withJavadocJar()
}

idea.module {
    isDownloadSources = true
    isDownloadJavadoc = true
}

jmh {
    jmhVersion = libs.versions.jmh.asProvider()
    includeTests = false
    zip64 = false
}

jvmdg.apiJar.addAll(
    jvmdg.apiJarDefault,
    file("buildSrc/build/libs/buildSrc.jar")
)

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jetbrains.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val sourcesJar by tasks.getting(Jar::class) {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }
}

val jarsToRelease = mutableSetOf<AbstractArchiveTask>(
    sourcesJar,
    tasks["javadocJar"] as Jar,
    tasks.jar.get(),
)

val compileAllTests by tasks.registering {
    group = "verification"
    description = "Compiles all tests"
}

downgradingJavaVersions.forEach {
    val displayName = it.displayName
    val underscoreName = displayName.replace('.', '_')

    val dgJar = tasks.register<DowngradeJar>("downgradeJar$underscoreName") {
        group = "jvmdowngrader"
        description = "Downgrades the jar to Java $displayName"
        downgradeTo = it.version
        inputFile = tasks.jar.get().archiveFile
        archiveClassifier = "downgraded-$underscoreName"

        jarsToRelease.add(this)
    }

    val dgTestCompile = tasks.register<DowngradeFiles>("downgradeTestCompile$underscoreName") {
        group = "jvmdowngrader"
        description = "Downgrades the test compile classpath to Java $displayName"
        downgradeTo = it.testVersion
        inputCollection = sourceSets.test.get().output
    }

    val dgTest = tasks.register<Test>("downgradedTest$underscoreName") {
        group = "verification"
        description = "Runs tests on the downgraded jar for Java $displayName"
        dependsOn(dgTestCompile, dgJar)
        classpath = dgJar.get().outputs.files +
                dgTestCompile.get().outputs.files +
                (sourceSets.test.get().runtimeClasspath - sourceSets.main.get().output - sourceSets.test.get().output)
        javaLauncher = javaToolchains.launcherFor {
            languageVersion.set(it.testVersion.toLanguageVersion())
        }
    }

    compileAllTests.configure { dependsOn(dgTestCompile) }
    tasks.assemble { dependsOn(dgJar) }
    tasks.test { dependsOn(dgTest) }
}

tasks {
    forEach {
        if (it.group == "jmh") {
            it.outputs.upToDateWhen { false }
            if(it is AbstractArchiveTask) {
                it.destinationDirectory = file("build/jmhlibs")
            }
        } else if (it is Jar) {
            // note - don't run advzip on jmh jars
            it.doLast {
                advzip(it.archiveFile.get().asFile)
            }
        }
    }

    jar {
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${rootProject.name}" }
        }
    }

    javadoc {
        val options = options as StandardJavadocDocletOptions
        options.addBooleanOption("Xdoclint:none", true)
    }

    withType<Test> {
        useJUnitPlatform()
        outputs.upToDateWhen { false }
    }

    withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    withType<GenerateModuleMetadata> {
        enabled = false
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = currentJavaVersion.majorVersion
    }

    filter { it.name == "githubRelease" || it is AbstractPublishToMaven }.forEach {
        it.dependsOn(assemble, check)
    }
}

githubRelease {
    token(providers.environmentVariable("GITHUB_TOKEN"))
    tagName = versionTagName
    targetCommitish = "master"
    releaseName = versionString
    releaseAssets(jarsToRelease.map { it.archiveFile })
}

publishing {
    repositories {
        if (!System.getenv("local_maven_url").isNullOrEmpty())
            maven(System.getenv("local_maven_url"))
    }

    publications {
        create<MavenPublication>("project_name"()) {
            jarsToRelease.forEach { jar ->
                artifact(jar.archiveFile)
            }
        }
    }
}

val advzipInstalled by lazy {
    try {
        ProcessBuilder("advzip", "-V").start().waitFor() == 0
    } catch (e: Exception) {
        false
    }
}

fun advzip(zip: File) {
    if (!advzipInstalled) {
        println("advzip is not installed; skipping re-deflation of ${zip.name}")
        return
    }

    val iterations = if(zip.length() < 20000) {
        if(isRelease) 1000
        else 100
    } else {
        if(isRelease) 100
        else 10
    }

    println("running advzip on ${zip.name} with $iterations iterations")

    exec {
        commandLine("advzip", "-z", "-4", "--iter=$iterations", zip.absolutePath)
    }.rethrowFailure().assertNormalExitValue()
}

fun JavaVersion.toLanguageVersion(): JavaLanguageVersion {
    return JavaLanguageVersion.of(this.majorVersion.toInt())
}