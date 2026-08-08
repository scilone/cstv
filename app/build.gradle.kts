import java.time.Duration
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val tmdbApiKey = System.getenv("TMDB_API_KEY") ?: localProperties.getProperty("TMDB_API_KEY") ?: ""

// Signature de release. La release se fabrique sur le poste de développement
// (voir scripts/release-local.sh) : les paramètres sont lus dans
// `keystore.properties`, jamais versionné, à la racine du dépôt. Les variables
// d'environnement gardent la priorité pour qu'une machine de build automatisée
// reste possible sans toucher au fichier.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

/** Valeur de signature : environnement, puis `keystore.properties`, puis [fallback]. */
fun signingSetting(envName: String, propertyName: String, fallback: String = ""): String =
    System.getenv(envName) ?: keystoreProperties.getProperty(propertyName) ?: fallback

android {
    namespace = "com.cstv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cstv.app"
        minSdk = 21
        targetSdk = 35
        // Phase 39 : synchronisés avec le dernier tag git poussé (voir AGENTS.md,
        // section "Checklist avant de conclure une tâche"). versionCode dérivé du
        // SemVer : major*10_000 + minor*100 + patch (marge de 0-99 par segment).
        versionCode = 17_315
        versionName = "1.73.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations.addAll(setOf("fr", "en"))
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    signingConfigs {
        create("release") {
            // Chemin relatif au module `app`, d'où le `../` pour un keystore
            // rangé à la racine du dépôt.
            storeFile = file(signingSetting("KEYSTORE_FILE", "storeFile", "release-keystore.jks"))
            storePassword = signingSetting("KEYSTORE_PASSWORD", "storePassword")
            keyAlias = signingSetting("KEY_ALIAS", "keyAlias")
            keyPassword = signingSetting("KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        debug {
            // Les ABI x86 servent aux émulateurs locaux, sans alourdir l'APK
            // universel de production avec une seconde copie des bibliothèques FFmpeg.
            ndk {
                abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a"))
            }
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            // `Modifier.focusRestorer`, qui fixe le point d'entrée du focus
            // dans les rangées TV.
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
    buildFeatures {
        compose = true
        // Nécessaire pour BuildConfig.DEBUG (logging HTTP conditionnel, Phase 36).
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Compatible with Kotlin 1.9.24
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Filet de sécurité anti-blocage des tests unitaires.
//
// Un test coroutine peut boucler pour toujours sans jamais échouer : le
// drainage du scheduler virtuel (`advanceUntilIdle`, nettoyage de `runTest`)
// n'est pas suspendable, donc ni le timeout interne de `runTest` ni une règle
// JUnit `Timeout` ne peuvent l'interrompre. Sans ce garde-fou, `./gradlew
// testDebugUnitTest` gèle indéfiniment (déjà rencontré plusieurs fois).
//
// `timeout` fait tuer la tâche par Gradle : le build échoue en quelques
// minutes au lieu de rester bloqué. La règle `Timeout` côté JUnit (voir
// `presentation/**Test.kt`) reste utile pour identifier le test coupable.
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(10))
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Compose Mobile
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Compose TV (Android TV support)
    implementation("androidx.tv:tv-material:1.0.0-alpha10")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Retrofit & OkHttp (Network)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Room (Local DB)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion") // Support PagingSource générées par Room

    // Paging 3 (Lazy Loading & Pagination)
    val pagingVersion = "3.2.1"
    implementation("androidx.paging:paging-runtime:$pagingVersion")
    implementation("androidx.paging:paging-compose:$pagingVersion")
    
    // DataStore & Crypto (Secure credentials storage)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Coil (Image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Media3 ExoPlayer (Video playing)
    val media3Version = "1.4.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    // NextLib : décodeurs FFmpeg software (EAC3/AC3/DTS/TrueHD...) pour les
    // appareils sans décodeur matériel de ces codecs. Aligné sur media3 1.4.0.
    implementation("com.github.anilbeesetti.nextlib:nextlib-media3ext:v0.8.2")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0")
}
