import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("android-flavors")
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1

val gitShortHash = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim().ifEmpty { "dev" }

// Local, gitignored build config (mobile/.env), baked into the APK at build time.
//
// Ben has no data of its own: the conversation is kept by the voiceControl server on the
// desktop, and this app reads it from there. So the base URL is that server's, and the
// token is its READ token (VOICE_CONTROL_READ_TOKEN) — never the one that sends commands.
val envFile = rootProject.file(".env")
val envProps = Properties()
if (envFile.exists()) envFile.inputStream().use { envProps.load(it) }
val apiBaseUrl = envProps.getProperty("API_BASE_URL", "http://10.7.0.2:3143/")
val apiToken = envProps.getProperty("API_TOKEN", "")

android {
    namespace = "com.automatelinux.ben"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.automatelinux.ben"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "v${gitCommitCount} (${gitShortHash})"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_TOKEN", "\"$apiToken\"")
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
        buildConfig = true
    }
}

dependencies {
    // Shared KMP module (commonMain code shared with iOS)
    implementation(project(":shared"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
}
