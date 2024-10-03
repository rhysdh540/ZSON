repositories.mavenCentral()
repositories.gradlePluginPortal()

dependencies {
    implementation("org.ow2.asm:asm:9.7")
    implementation("xyz.wagyourtail.jvmdowngrader:xyz.wagyourtail.jvmdowngrader.gradle.plugin:${libs.versions.jvmdg.get()}")
}