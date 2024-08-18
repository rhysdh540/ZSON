pluginManagement {
    repositories {
        gradlePluginPortal {
            content {
                excludeGroup("org.apache.logging.log4j")
            }
        }
    }
}

buildscript {
    dependencies {
        classpath("org.apache.commons:commons-io:1.3.2")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.7.0")
}

rootProject.name = "zson"

