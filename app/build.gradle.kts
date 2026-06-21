plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")

    kotlin("plugin.serialization") version "2.2.0"
}

android {

    namespace = "com.romaster.livewallengine"

    compileSdk = 36

    defaultConfig {

        applicationId = "com.romaster.livewallengine"

        minSdk = 26

        targetSdk = 36

        versionCode = 1

        versionName = "1.0.0-alpha1"
    }

    buildTypes {
        
        release {
            
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17

        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {

        jvmTarget = "17"
    }
}

dependencies {

    implementation(
        "androidx.core:core-ktx:1.17.0"
    )

    implementation(
        "androidx.appcompat:appcompat:1.7.1"
    )

    implementation(
        "com.google.android.material:material:1.13.0"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0"
    )

    implementation(
        "androidx.media3:media3-exoplayer:1.8.0"
    )

    implementation(
        "androidx.media3:media3-ui:1.8.0"
    )

    implementation(
        "androidx.media3:media3-common:1.8.0"
    )
}