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

        // ABI seçimi aşağıdaki `splits.abi` bloğunda (arm64 telefon, x86_64 emülatör);
        // sherpa-onnx AAR'ı yalnız bu ikisi için paketlenir.
    }

    buildTypes {
        debug {
            // Telefonda yüklü Hermes / Hermes V2 ile ÇAKIŞMASIN: ayrı paket
            // kimliği (V3) ve ayrı ad — üzerine yazmaz, yanına ayrı uygulama
            // olarak kurulur (ayarları ve verisi de ayrıdır).
            applicationIdSuffix = ".v3"
            versionNameSuffix = "-v3"
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

    // Mimariye göre ayrı APK: telefon (arm64) ~yarı boyut; x86_64 yalnız emülatör için.
    // (Tek evrensel APK 100 MB'ı aşıyordu — GitHub dosya sınırı.)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

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
    // V3 "Hey Jarvis" uyandırma kelimesi — openWakeWord modelleri (TFLite).
    // sherpa-onnx'in kendi onnxruntime'ı ile çakışmasın diye ONNX değil TFLite.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
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
