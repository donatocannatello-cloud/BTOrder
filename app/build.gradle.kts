plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.example.btorder"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.example.btorder"
        // API 31 richiesta da AudioManager.setCommunicationDevice e TelephonyCallback,
        // usati dall'instradamento automatico delle chiamate.
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            // Keystore di debug fissa e committata nel repo: senza di questa, ogni build su
            // un runner CI "pulito" ne genererebbe una diversa (Gradle la crea al volo se non
            // la trova), firmando ogni APK con una chiave differente. Android rifiuta di
            // installare un APK "aggiornato" se la firma non coincide con quella già installata
            // (INSTALL_FAILED_UPDATE_INCOMPATIBLE), costringendo a disinstallare prima di ogni
            // aggiornamento. Con una chiave fissa gli aggiornamenti installano normalmente sopra
            // la versione precedente.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Persistenza dei dispositivi di fiducia, delle automazioni e della priorità chiamate
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
