import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Release signing config. Reads from environment variables (set in the
// android-release.yml workflow) or, when building locally, falls back to
// `keystore.properties` at the repo root (gitignored). If no keystore is
// available the release build is still possible — Android Gradle Plugin will
// just emit an unsigned APK and the existing signing-step in CI will sign it
// with a throwaway key (legacy behaviour, kept as a safety net).
val keystoreFromEnv: File? = System.getenv("KEYSTORE_FILE")?.let(::File)
val keystorePropsFile = rootProject.file("keystore.properties")
val localKeystoreProps: Properties? = if (keystorePropsFile.exists()) {
    Properties().apply { load(FileInputStream(keystorePropsFile)) }
} else null

fun secret(envName: String, propName: String): String? =
    System.getenv(envName) ?: localKeystoreProps?.getProperty(propName)

val resolvedStorePassword = secret("KEYSTORE_PASSWORD", "storePassword")
val resolvedKeyAlias = secret("KEY_ALIAS", "keyAlias")
val resolvedKeyPassword = secret("KEY_PASSWORD", "keyPassword")
val resolvedKeystoreFile: File? = keystoreFromEnv
    ?: localKeystoreProps?.getProperty("storeFile")?.let { rootProject.file(it) }

val signingReady = resolvedKeystoreFile?.exists() == true &&
    !resolvedStorePassword.isNullOrBlank() &&
    !resolvedKeyAlias.isNullOrBlank() &&
    !resolvedKeyPassword.isNullOrBlank()

android {
    namespace = "de.astronarren.allsky"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.astronarren.allsky"
        minSdk = 29
        targetSdk = 35
        versionCode = 62
        versionName = "3.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = resolvedKeystoreFile
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
                // Both v1 + v2 signing schemes — needed for compatibility
                // with Android 9 (P) and below alongside modern installs.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    
    lint {
        abortOnError = false
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // Fragment
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    
    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")

    // OkHttp — pinned explicitly so Coil and our auth interceptor share one stack
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Retrofit for API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // HTML Parser
    implementation("org.jsoup:jsoup:1.17.2")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Media3 dependencies
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")

    // Palette for dynamic theming
    implementation("androidx.palette:palette-ktx:1.0.0")

    // SSH client for the optional focus-motor feature. The maintained mwiede
    // fork of JSch is what we want here — pure-Java, ~300 KB, supports modern
    // KEX/HostKey algorithms (ed25519, curve25519-sha256) that the original
    // unmaintained JSch chokes on against current OpenSSH defaults.
    implementation("com.github.mwiede:jsch:0.2.21")

    // Reorderable LazyColumn/LazyRow for the layout-editor drag-to-reorder.
    // Tiny (~70 KB), zero-transitive-dep, MIT-licensed; integrates cleanly
    // with the standard LazyListState so we keep using LazyColumn for the
    // scroll viewport.
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // SGP4 propagator for the Tonight card's satellite-passes row. Faithful
    // Java port of Vallado's reference implementation, ~80 KB, MIT-licensed.
    // Unmaintained since ~2014 but the SGP4 model itself is stable, so the
    // abandonware risk is acceptable. Used by SatelliteRepository for TLE
    // parsing + pass prediction against the user's saved lat/lon.
    implementation("uk.me.g4dpz:predict4java:1.1.3")
}