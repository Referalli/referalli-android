pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // On JitPack the Kotlin SDK comes from JitPack's own build of referalli-kotlin.
        if (System.getenv("JITPACK") == "true") maven("https://jitpack.io")
    }
}

rootProject.name = "referalli-android"

// Build against the Kotlin SDK source when it sits next to this module: the monorepo layout
// (packages/sdk-kotlin), or CI checking out referalli-kotlin beside referalli-android. Otherwise
// com.referalli:referalli-kotlin resolves from Maven Central.
val kotlinSdkDir = settingsDir.resolve(providers.gradleProperty("referalli.kotlinSdkDir").getOrElse("../sdk-kotlin"))
if (kotlinSdkDir.resolve("build.gradle.kts").exists()) {
    includeBuild(kotlinSdkDir) {
        dependencySubstitution {
            substitute(module("com.referalli:referalli-kotlin")).using(project(":"))
        }
    }
}
