// Lets the pure-Kotlin core build on its own (no Android SDK needed): `cd core && gradle test`.
// When building from the repo root this file is ignored and :core is part of the full build.
pluginManagement {
    repositories {
        mavenLocal()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "core"
