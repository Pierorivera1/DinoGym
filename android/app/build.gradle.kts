import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.pierorivera1.dinogym"
    // The 2026 AndroidX libraries need to compile against API 37. targetSdk stays 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.pierorivera1.dinogym"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing (decision in DECISIONS.md, "Release signing"). The keystore never enters
    // git. The values come from environment variables (the release workflow sets them from
    // GitHub secrets) or, on the owner's machine, from android/keystore.properties (git-ignored).
    val signing = releaseSigning()
    if (signing != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signing.getValue("storeFile"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build and a release build install side by side.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            // Unsigned when no keystore is configured (CI builds on every push, lint), so
            // only the release workflow and the owner's machine produce an installable APK.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME and VERSION_CODE, shown in Settings.
        buildConfig = true
    }

    androidResources {
        // The app ships English and Spanish only (decision 12).
        localeFilters += listOf("en", "es")
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources (fonts, strings, drawables) to render Compose.
            isIncludeAndroidResources = true
            all {
                // Roborazzi writes screenshots only in "record" mode. Nothing compares them yet;
                // they are for looking at (app/build/outputs/roborazzi/).
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room3 {
    // Exported schemas are committed so a schema change shows up in review.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM screenshots of Compose UI, without an emulator (Robolectric renders, Roborazzi saves PNGs).
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}

/**
 * The release signing values, or null when none are configured (the build is then unsigned).
 * Environment variables win: DINOGYM_KEYSTORE_FILE, DINOGYM_KEYSTORE_PASSWORD,
 * DINOGYM_KEY_ALIAS, DINOGYM_KEY_PASSWORD. Otherwise android/keystore.properties with the keys
 * storeFile, storePassword, keyAlias, keyPassword.
 */
fun releaseSigning(): Map<String, String>? {
    val fromEnv = mapOf(
        "storeFile" to System.getenv("DINOGYM_KEYSTORE_FILE"),
        "storePassword" to System.getenv("DINOGYM_KEYSTORE_PASSWORD"),
        "keyAlias" to System.getenv("DINOGYM_KEY_ALIAS"),
        "keyPassword" to System.getenv("DINOGYM_KEY_PASSWORD"),
    )
    if (fromEnv.values.all { !it.isNullOrBlank() }) return fromEnv.mapValues { it.value!! }

    val file = rootProject.file("keystore.properties")
    if (!file.exists()) return null
    val props = Properties().apply { file.inputStream().use { load(it) } }
    val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val missing = keys.filter { props.getProperty(it).isNullOrBlank() }
    require(missing.isEmpty()) { "keystore.properties is missing: ${missing.joinToString()}" }
    return keys.associateWith { props.getProperty(it) }
}
