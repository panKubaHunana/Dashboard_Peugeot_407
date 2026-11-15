plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Číslo verze dodává GitHub Actions (pořadové číslo běhu); při ručním sestavení je 1.
val ciVersion = (System.getenv("VERSION_CODE") ?: "1").toInt()
// Stálý podpisový klíč, aby šla každá nová verze nainstalovat přes starou.
val sharedKeystore = rootProject.file("keystore/dash407.jks")

android {
    namespace = "cz.kuba.dash407"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.kuba.dash407"
        minSdk = 29
        targetSdk = 34
        versionCode = ciVersion
        versionName = "2.14"
        // GitHub Actions dodá přesné označení vydání (např. 2.7 nebo 2.7.23),
        // aby aplikace při kontrole aktualizací poznala, kterou verzi má.
        System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }?.let { versionName = it }
    }

    signingConfigs {
        if (sharedKeystore.exists()) {
            create("shared") {
                storeFile = sharedKeystore
                storePassword = "dash407"
                keyAlias = "dash407"
                keyPassword = "dash407"
            }
        }
    }

    buildTypes {
        debug {
            if (sharedKeystore.exists()) signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
