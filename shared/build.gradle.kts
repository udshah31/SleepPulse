plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

repositories {
    google()
    mavenCentral()
}

val enableIosTargets = providers.gradleProperty("enableIosTargets")
    .map(String::toBoolean)
    .orElse(false)
    .get()

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    if (enableIosTargets) {
        // iOS compilation requires Xcode and its command-line tools. Keep this
        // opt-in so Android/common builds remain usable on machines without it.
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                // :app builds the database and implements the DAOs' consumers, so Room is part of the API.
                api("androidx.room:room-runtime:2.8.4")
                api("androidx.sqlite:sqlite-bundled:2.5.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

android {
    namespace = "com.sleeppulse.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Committed; app's SleepPulseDatabaseMigrationsTest checks it against the DB version, and the
// instrumented migration test reads it as assets.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.8.4")
    if (enableIosTargets) {
        add("kspIosX64", "androidx.room:room-compiler:2.8.4")
        add("kspIosArm64", "androidx.room:room-compiler:2.8.4")
        add("kspIosSimulatorArm64", "androidx.room:room-compiler:2.8.4")
    }
}
