plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.example.theremin"
    // Google Play richiede per le app nuove l'ultima versione di Android come target (Android 16)
    compileSdk = 36

    defaultConfig {
        // Identità definitiva sul Play Store: non va più cambiata
        applicationId = "it.donatocannatello.theremincromatico"
        // API 26: AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
        minSdk = 26
        targetSdk = 36
        manifestPlaceholders["nomeApp"] = "Theremin Cromatico"
        // Cresce a ogni build su GitHub Actions, così ogni APK si installa come aggiornamento
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 1
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "dev"}"

        // Solo i processori dei telefoni (ARM): le librerie native di MediaPipe per x86
        // servono solo agli emulatori e raddoppierebbero la dimensione dell'APK
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Chiave di debug fissa, salvata nel repository: senza di essa ogni runner CI genera una
        // chiave nuova e Android rifiuta l'aggiornamento ("pacchetto in conflitto").
        // Solo per le build di debug/distribuzione interna, non per il Play Store.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Chiave di caricamento per il Play Store: NON è nel repository (che è pubblico). Su GitHub
        // Actions arriva dai Secrets; in locale si passa con le stesse variabili d'ambiente.
        val keystoreCaricamento = System.getenv("THEREMIN_UPLOAD_STORE_FILE")
        if (keystoreCaricamento != null && file(keystoreCaricamento).exists()) {
            create("caricamento") {
                storeFile = file(keystoreCaricamento)
                storePassword = System.getenv("THEREMIN_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("THEREMIN_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("THEREMIN_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Pacchetto distinto: la versione di prova (APK da GitHub) e quella del Play Store,
            // firmate con chiavi diverse, possono stare sullo stesso telefono senza conflitti
            applicationIdSuffix = ".debug"
            manifestPlaceholders["nomeApp"] = "Theremin (prova)"
        }
        release {
            signingConfigs.findByName("caricamento")?.let { signingConfig = it }
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
    // 1.4+: librerie native allineate a pagine da 16 KB, come richiesto da Google Play
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Riconoscimento della mano (21 punti per mano), eseguito interamente sul telefono
    // Versione recente: librerie native compatibili con pagine da 16 KB (requisito di Google Play)
    implementation("com.google.mediapipe:tasks-vision:0.10.35")

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
