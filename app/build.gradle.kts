import java.util.Properties

plugins { id("com.android.application"); kotlin("plugin.compose") }

val releaseSigningFile = providers.gradleProperty("dxReleaseSigningFile")
    .orElse(providers.environmentVariable("DX_RELEASE_SIGNING_FILE"))
val releaseSigning = releaseSigningFile.orNull?.let { path ->
    Properties().apply { rootProject.file(path).inputStream().use { load(it) } }
}

android {
    namespace = "com.dynorixz.buttonscontrol"
    compileSdk { version = release(37) }
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.dynorixz.buttonscontrol"
        minSdk = 30
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.2-ultra-test"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (releaseSigning != null) create("release") {
            storeFile = rootProject.file(requireNotNull(releaseSigning.getProperty("storeFile")))
            storePassword = requireNotNull(releaseSigning.getProperty("storePassword"))
            keyAlias = requireNotNull(releaseSigning.getProperty("keyAlias"))
            keyPassword = requireNotNull(releaseSigning.getProperty("keyPassword"))
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.wear.compose:compose-material3:1.7.1")
    implementation("androidx.wear:wear-input:1.2.0")
    implementation("androidx.wear:wear-remote-interactions:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.core:core-ktx:1.16.0")
}
