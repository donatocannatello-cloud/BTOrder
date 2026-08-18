plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.example.menumostro"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.example.menumostro"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "2.1"
    }

    // Keystore di debug fisso e versionato (debug.keystore, credenziali di default
    // di Android): senza questo, ogni ambiente (Android Studio locale, ogni run di
    // CI) genera/usa un keystore di debug diverso, quindi le firme non combaciano e
    // Android rifiuta l'aggiornamento di un APK già installato ("pacchetto in
    // conflitto"). Firmando sempre con lo stesso keystore, gli APK di debug si
    // aggiornano l'uno sull'altro senza doverli disinstallare prima.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
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
    implementation("androidx.compose.foundation:foundation")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
