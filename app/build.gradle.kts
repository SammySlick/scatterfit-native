plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.scatterbrain.scatterfit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scatterbrain.scatterfit"
        minSdk = 28
        targetSdk = 35
        // CI stamps the run number into the version so every sideload is
        // self-identifying (Settings > Apps > ScatterFit shows it). Locally
        // built (no CI env) it falls back to a static version.
        // CI builds: versionCode = run number (69, 70, ...).
        // Local AS builds (no CI env): 100000 — always higher than any CI run
        // number for the foreseeable future, so Run > installs over CI builds
        // instead of failing with "device already has a newer version"
        // (seen live 2026-10-09 03:47: phone had CI versionCode 62-64, local
        // build was 1, installer refused as a downgrade).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 100000
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "local")
    }

    signingConfigs {
        // Pinned debug keystore: every CI build signs with the SAME key so
        // sideloaded updates install over each other and KEEP their Health
        // Connect permissions (ephemeral CI keystores meant every build was a
        // different "publisher" — uninstall/reinstall wiped the grants).
        create("stableDebug") {
            storeFile = file("../signing/debug.keystore")
            storePassword = "scatterfit"
            storeType = "PKCS12"
            keyAlias = "scatterfit-debug"
            keyPassword = "scatterfit"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
