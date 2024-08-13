pluginManagement {
    repositories {
        gradlePluginPortal {
            content {
                excludeGroup("org.apache.logging.log4j")
            }
        }
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
    }
}

buildscript {
    dependencies {
        classpath("org.apache.commons:commons-io:1.3.2")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.7.0")
    id("org.ajoberstar.grgit") version("5.2.2") apply(false)
    id("com.github.breadmoirai.github-release") version("2.4.1") apply(false)
    id("xyz.wagyourtail.jvmdowngrader") version("1.0.0") apply(false)
    id("me.champeau.jmh") version("0.7.2") apply(false)
}

rootProject.name = "zson"

