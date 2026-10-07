pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ScatterFit"
include(":app")
