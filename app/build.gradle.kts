plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.yarkoody.scalesync"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.yarkoody.scalesync"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
    }

    // Optional fixed signing key from CI secrets, so each build installs over the previous one.
    // Without it, builds are signed with the runner's debug key.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("personal") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_ALIAS") ?: "scalesync"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("personal") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
