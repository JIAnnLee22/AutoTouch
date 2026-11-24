plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "jiannlee22.funnytool"
    compileSdk = 35

    defaultConfig {
        applicationId = "jiannlee22.funnytool"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("releaseVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("releaseVersion").orElse("0.1.5").get()
    }
    val signingValues = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
        .map { System.getenv(it) }
    val suppliedSigningValues = signingValues.count { !it.isNullOrBlank() }
    require(suppliedSigningValues == 0 || suppliedSigningValues == 4) { "Provide all four ANDROID signing variables, or none." }
    val privateSigning = suppliedSigningValues == 4
    signingConfigs {
        create("release") {
            // Explicitly public, app-specific shared development key; never use for production identity.
            storeFile = if (privateSigning) file(signingValues[0]!!) else rootProject.file("signing/funnytool-shared.p12")
            storePassword = if (privateSigning) signingValues[1] else "funnytool-public-signing"
            keyAlias = if (privateSigning) signingValues[2] else "funnytool"
            keyPassword = if (privateSigning) signingValues[3] else "funnytool-public-signing"
            if (!privateSigning) storeType = "PKCS12"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.savedstate:savedstate-ktx:1.3.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
