plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.lockscreenapps"
    compileSdk = 34

    signingConfigs {
        // Permanent key so updates install over the old version and keep your settings
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "quickapps123"
            keyAlias = "quickapps"
            keyPassword = "quickapps123"
            storeType = "pkcs12"
        }
    }

    defaultConfig {
        applicationId = "com.example.lockscreenapps"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "4.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
