plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "fr.opencarbu"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.opencarbu"
        // Android 10: the oldest version the project guarantees. A higher floor means
        // fewer old-device quirks to test and maintain (java.time, HTTPS certificates,
        // storage/location rules...). Lowering it later is possible but needs testing.
        minSdk = 29
        targetSdk = 35
        versionCode = 55
        versionName = "0.21.4"
    }

    buildTypes {
        release {
            // Shrinking keeps the APK small, which matters for download size and startup.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // To try the minified build on a phone before publication (there is no release key yet):
            //   ./gradlew assembleRelease -PdebugSignRelease
            // The APK is then signed with the debug key: for testing only, never to be published.
            if (project.hasProperty("debugSignRelease")) signingConfig = signingConfigs.getByName("debug")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Background work and networking
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)            // HTTP client (gzip, timeouts)
    implementation(libs.moshi)             // streaming JSON reader only (no reflection/codegen)

    // Map: MapLibre Native (open source, BSD licence, no API key). Needs OpenGL ES 3.0.
    implementation(libs.maplibre)

    // Pure JVM unit tests (no emulator needed): ./gradlew test
    testImplementation(libs.kotlin.test)
}
