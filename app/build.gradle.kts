plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hermes.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hermes.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }

        // Tur-21: yerel TTS motoru (sherpa-onnx, app/libs AAR) yalnız bu iki
        // ABI'yi taşıyor — telefon arm64, emülatör x86_64. Diğerleri paketlenmez.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            // Mevcut yüklü Hermes ile ÇAKIŞMASIN: ayrı paket kimliği (V2) ve
            // ayrı ad — telefonda ikinci uygulama olarak yanına kurulur.
            applicationIdSuffix = ".v2"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Canlı görüntü — Gemini Live'a kare akıtmak için
    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // Android Auto — şablon tabanlı araç arayüzü
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    // car.app, guava'yı yalnız çalışma zamanına koyup derlemede boş
    // listenablefuture saplamasını dayatıyor; CameraX'in ListenableFuture'ı
    // derlemede bulabilmesi için guava'yı açıkça istiyoruz.
    implementation("com.google.guava:guava:31.1-android")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Shizuku — kabuk (UID 2000) yetkisiyle komut çalıştırma. Erişilebilirlik
    // servisi yerine bu: Android 17 o API'yi otomasyon uygulamalarına kapatıyor.
    val shizuku = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizuku")
    implementation("dev.rikka.shizuku:provider:$shizuku")

    // Tur-21: yerel (çevrimdışı) TTS motoru — sherpa-onnx 1.13.8 (Apache-2.0).
    // MavenCentral'da POM'u olmadığı için resmî GitHub sürüm AAR'ı depoya
    // alındı (app/libs, sha256 633c24321e06b1fe... — RAPOR'da tam değer).
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
