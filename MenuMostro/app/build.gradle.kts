plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.freebimbogames.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.freebimbogames.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "5.1"
    }

    // Keystore di debug fisso e versionato (debug.keystore, credenziali di default
    // di Android): senza questo, ogni ambiente (Android Studio locale, ogni run di
    // CI) genera/usa un keystore di debug diverso, quindi le firme non combaciano e
    // Android rifiuta l'aggiornamento di un APK già installato ("pacchetto in
    // conflitto"). Firmando sempre con lo stesso keystore, gli APK di debug si
    // aggiornano l'uno sull'altro senza doverli disinstallare prima.
    // Chiave di firma per la pubblicazione su Google Play: a differenza del keystore
    // di debug, NON è mai committata (il repo è pubblico). Le credenziali arrivano
    // solo da variabili d'ambiente, valorizzate dal workflow di release a partire
    // dai secret del repository GitHub. Se RELEASE_KEYSTORE_PATH non è impostata
    // (es. in locale o nella build di debug normale) il buildType "release" resta
    // semplicemente non firmato: serve solo a generare l'AAB da caricare su Play.
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
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
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.compose.foundation:foundation")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
