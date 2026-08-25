plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versione applicata alla release: la CI la sovrascrive leggendo il tag git
// (es. v1.2.0 -> versionName "1.2.0", versionCode calcolato dal numero della run).
// In locale, senza parametri, si compila come build di sviluppo.
val frattalogicVersionName = (project.findProperty("frattalogicVersionName") as String?) ?: "0.1.0-dev"
val frattalogicVersionCode = (project.findProperty("frattalogicVersionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "it.example.frattalogic"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.example.frattalogic"
        minSdk = 26
        targetSdk = 34
        versionCode = frattalogicVersionCode
        versionName = frattalogicVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmato con il keystore di debug (auto-generato da AGP): sufficiente per
            // installare l'APK manualmente e per aggiornarlo tra una release e l'altra,
            // senza dover gestire segreti di firma in CI.
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
