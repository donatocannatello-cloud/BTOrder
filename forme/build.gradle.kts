plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.example.forme"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.donatocannatello.incastraforme"
        minSdk = 26
        targetSdk = 36
        manifestPlaceholders["nomeApp"] = "Incastra le Forme"
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
        // Chiave di debug fissa (la stessa del theremin): ogni APK di CI si installa come aggiornamento
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["nomeApp"] = "Incastra le Forme (prova)"
        }
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

    // Fotocamera anteriore a tutto schermo + analisi dei fotogrammi
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Riconoscimento delle due mani (21 punti per mano), eseguito interamente sul telefono
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
