plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.example.dueordie"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.intellectexplorer.mora"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "2.3.59"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val moraSigningReady = listOf("MORA_KEYSTORE_PASSWORD", "MORA_KEY_PASSWORD")
        .all { providers.gradleProperty(it).orNull?.isNotBlank() == true } &&
        providers.gradleProperty("MORA_KEY_ALIAS").orNull?.isNotBlank() == true &&
        rootProject.file("keystore/mora-release.jks").isFile

    signingConfigs {
        create("moraRelease") {
            storeFile = rootProject.file("keystore/mora-release.jks")
            storePassword = providers.gradleProperty("MORA_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.gradleProperty("MORA_KEY_ALIAS").orNull
            keyPassword = providers.gradleProperty("MORA_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        debug {
            // Use the official key only when credentials are supplied outside source control.
            if (moraSigningReady) signingConfig = signingConfigs.getByName("moraRelease")
        }
        release {
            if (moraSigningReady) signingConfig = signingConfigs.getByName("moraRelease")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation("com.microsoft.clarity:clarity-compose:3.+")
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
}
