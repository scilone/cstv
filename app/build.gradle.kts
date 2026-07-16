plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.poc.iptvxtream"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.poc.iptvxtream"
        // minSdk 23 depuis l'audit #4 : requis par media3 1.9 et par le
        // chiffrement Keystore AES-GCM (audit #3). Voir AGENTS.md.
        minSdk = 23
        targetSdk = 35
        // Phase 39 : synchronisés avec le dernier tag git poussé (voir AGENTS.md,
        // section "Checklist avant de conclure une tâche"). versionCode dérivé du
        // SemVer : major*10_000 + minor*100 + patch (marge de 0-99 par segment).
        versionCode = 12_001
        versionName = "1.20.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Export des schémas Room (JSON versionnés, requis par MigrationTestHelper).
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE") ?: "release-keystore.jks"
            storeFile = file(keystoreFile)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
    buildFeatures {
        compose = true
        // Nécessaire pour BuildConfig.DEBUG (logging HTTP conditionnel, Phase 36).
        buildConfig = true
    }
    testOptions {
        // Les tests JVM traversent android.util.Log (ex : CredentialsManager) :
        // no-op au lieu de "not mocked".
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose Mobile
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Compose TV (Android TV support)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Retrofit & OkHttp (Network)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Room (Local DB)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore & Crypto (Secure credentials storage)
    implementation(libs.androidx.datastore.preferences)
    // TODO(2026-07): security-crypto (déprécié) n'est plus utilisé que par
    // EncryptedPrefsLegacySource pour migrer les anciens credentials vers le
    // chiffrement Keystore maison — retirer la dépendance et la classe une
    // version après la release contenant la migration.
    implementation(libs.androidx.security.crypto)

    // Coil (Image loading)
    implementation(libs.coil.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Media3 ExoPlayer (Video playing)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    // Instrumented Testing (migrations Room — nécessite un émulateur/device,
    // voir AGENTS.md : ./gradlew connectedDebugAndroidTest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
