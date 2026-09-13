plugins {
    id("com.android.application") version "8.7.3"
}

android {
    namespace = "com.hermes.sharesender"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hermes.sharesender"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
