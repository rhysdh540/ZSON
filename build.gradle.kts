import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
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

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

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
        if (tag1.commit.dateTime == tag2.commit.dateTime)
            if (tag1.name.length != tag2.name.length)
                return@sortedWith tag1.name.length.compareTo(tag2.name.length)
            else
                return@sortedWith tag1.name.compareTo(tag2.name)
        else
            return@sortedWith tag2.commit.dateTime.compareTo(tag1.commit.dateTime)
    }
    .dropWhile { tag -> tag.commit.dateTime > headDateTime }

val isExternalCI = (rootProject.properties["external_publish"] as String?).toBoolean()
val isRelease = rootProject.hasProperty("release_channel") || isExternalCI
val releaseIncrement = if (isExternalCI) 0 else 1
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
                else if (patchHistory.contains(maxPatch.toString()))
                    maxPatch + releaseIncrement
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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of("java_version"()))
    }

    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jetbrains.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.javadoc {
    val options = options as StandardJavadocDocletOptions
    options.addBooleanOption("Xdoclint:none", true)
}

jvmdg.apiJar.add(jvmdg.apiJarDefault)
jvmdg.apiJar.add(file("buildSrc/build/libs/buildSrc.jar"))

tasks.downgradeJar {
    dependsOn(tasks.jar)
    downgradeTo = JavaVersion.VERSION_1_5
    archiveClassifier = "downgraded-8"
}

val downgradeJar17 = tasks.register<DowngradeJar>("downgradeJar17") {
    dependsOn(tasks.jar)
    downgradeTo = JavaVersion.VERSION_17
    inputFile = tasks.jar.get().archiveFile
    archiveClassifier = "downgraded-17"
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }

    finalizedBy(tasks.downgradeJar, downgradeJar17)
}

val sourcesJar = tasks.getByName<Jar>("sourcesJar") {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }
}

tasks.assemble {
    dependsOn(tasks.jar, sourcesJar, downgradeJar17)
}

val downgradedTest by tasks.registering(Test::class) {
    group = "verification"
    useJUnitPlatform()
    dependsOn(tasks.downgradeJar)
    outputs.upToDateWhen { false }
    classpath = tasks.downgradeJar.get().outputs.files +
            sourceSets.test.get().output +
            sourceSets.test.get().runtimeClasspath - sourceSets.main.get().output
}

val downgraded17Test by tasks.registering(Test::class) {
    group = "verification"
    useJUnitPlatform()
    dependsOn(downgradeJar17)
    outputs.upToDateWhen { false }
    classpath = downgradeJar17.get().outputs.files +
            sourceSets.test.get().output +
            sourceSets.test.get().runtimeClasspath - sourceSets.main.get().output
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    finalizedBy(downgradedTest, downgraded17Test)
}

jmh {
    jmhVersion = libs.versions.jmh.asProvider()
    includeTests = false
    zip64 = false
}

tasks.forEach {
    if (it.group == "jmh") {
        it.outputs.upToDateWhen { false }
    }
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

val advzipInstalled by lazy {
    try {
        ProcessBuilder("advzip", "-V").start().waitFor() == 0
    } catch (e: Exception) {
        false
    }
}

tasks.withType<Jar> {
    if(group == "jmh") return@withType // jmh jar is broken for some reason
    doLast {
        if (!advzipInstalled) {
            println("advzip is not installed; skipping re-deflation of $name")
            return@doLast
        }

        val zip = archiveFile.get().asFile

        try {
            val iterations = if(zip.length() < 20000) {
                if(isRelease) 1000
                else 100
            } else {
                if(isRelease) 100
                else 10
            }

            logger.info("running advzip on $name with ")

            val process = ProcessBuilder("advzip", "-z", "-4", "--iter=$iterations", zip.absolutePath)
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("advzip finished with exit code $exitCode.\n${process.errorStream.bufferedReader().readText()}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to compress $name", e)
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "java_version"()
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

githubRelease {
    setToken(providers.environmentVariable("GITHUB_TOKEN"))
    setTagName(versionTagName)
    setTargetCommitish("master")
    setReleaseName(versionString)
    setReleaseAssets(tasks.jar.get().archiveFile, sourcesJar.archiveFile)
}

tasks.githubRelease {
    dependsOn(tasks.assemble, tasks.check)
}

publishing {
    repositories {
        if (!System.getenv("local_maven_url").isNullOrEmpty())
            maven(System.getenv("local_maven_url"))
    }

    publications {
        create<MavenPublication>("project_name"()) {
            artifact(tasks.jar) // latest java
            artifact(downgradeJar17) // java 17
            artifact(tasks.downgradeJar) // java 8
            artifact(sourcesJar) // java 21 sources
            artifact(tasks["javadocJar"])
        }
    }
}

tasks.withType<AbstractPublishToMaven> {
    dependsOn(tasks.assemble, tasks.check)
}