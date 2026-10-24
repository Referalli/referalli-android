plugins {
    id("com.android.library") version "8.11.1"
    // Same Kotlin as referalli-kotlin, whose metadata this module reads.
    id("org.jetbrains.kotlin.android") version "2.4.10"
    `maven-publish`
    signing
}

group = "com.referalli"
version = "0.2.0"

android {
    namespace = "com.referalli.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The tracker core (HttpURLConnection, no java.net.http).
    // JitPack (JITPACK=true) builds from the GitHub tag: depend on its build of the Kotlin SDK at the
    // same version. Everywhere else it is the Maven Central artifact (or the included build).
    api(if (System.getenv("JITPACK") == "true") "com.github.Referalli:referalli-kotlin:${project.version}" else "com.referalli:referalli-kotlin:${project.version}")
    implementation("com.android.installreferrer:installreferrer:2.2")
    // Optional: OkHttpTrackerTransport is usable only when the app already depends on OkHttp.
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.referalli"
            artifactId = "referalli-android"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Referalli Android SDK")
                description.set("Referalli app attribution for Android: app links, Play Install Referrer, deferred deep links and signup tracking.")
                url.set("https://referalli.com")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("referalli")
                        name.set("Referalli")
                    }
                }
                scm {
                    url.set("https://github.com/referalli/referalli-android")
                    connection.set("scm:git:https://github.com/referalli/referalli-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/referalli/referalli-android.git")
                }
            }
        }
    }
    repositories {
        // Staged here, then uploaded to the Maven Central Portal by the release workflow (WS5).
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging"))
        }
    }
}

signing {
    // Only when publishing from CI: SIGNING_KEY / SIGNING_PASSWORD (ASCII-armored GPG key).
    val key = System.getenv("SIGNING_KEY")
    if (key != null) {
        useInMemoryPgpKeys(key, System.getenv("SIGNING_PASSWORD"))
        sign(publishing.publications)
    }
}
