repositories.mavenCentral()
repositories.gradlePluginPortal()

dependencies {
    implementation("org.ow2.asm:asm:9.7")
    compileOnly("xyz.wagyourtail.jvmdowngrader:xyz.wagyourtail.jvmdowngrader.gradle.plugin:1.1.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}