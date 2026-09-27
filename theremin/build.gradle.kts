plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.example.theremin"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.example.theremin"
        // API 26: AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
        minSdk = 26
        targetSdk = 34
        // Cresce a ogni build su GitHub Actions, così ogni APK si installa come aggiornamento
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 1
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "dev"}"

        // Solo i processori dei telefoni (ARM): le librerie native di MediaPipe per x86
        // servono solo agli emulatori e raddoppierebbero la dimensione dell'APK
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Chiave di debug fissa, salvata nel repository: senza di essa ogni runner CI genera una
    // chiave nuova e Android rifiuta l'aggiornamento ("pacchetto in conflitto").
    // Solo per le build di debug/distribuzione interna, non per il Play Store.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // Il modello di MediaPipe va letto così com'è dagli asset, senza compressione
    androidResources {
        noCompress += "task"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // Fotocamera anteriore: anteprima + analisi dei fotogrammi
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Riconoscimento della mano (21 punti per mano), eseguito interamente sul telefono
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    testImplementation("junit:junit:4.13.2")
}

// Modello di MediaPipe per i punti della mano: non è nel repository (7,8 MB) ma viene scaricato
// dal server ufficiale di Google alla prima compilazione e salvato negli asset.
val scaricaModelloMano by tasks.registering {
    val destinazione = layout.projectDirectory.file("src/main/assets/hand_landmarker.task").asFile
    outputs.file(destinazione)
    doLast {
        if (!destinazione.exists() || destinazione.length() == 0L) {
            destinazione.parentFile.mkdirs()
            val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
            logger.lifecycle("Scarico il modello della mano da $url")
            uri(url).toURL().openStream().use { input ->
                destinazione.outputStream().use { input.copyTo(it) }
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(scaricaModelloMano) }
