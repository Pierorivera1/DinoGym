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

        // The Web OAuth client ID the backend checks as the Google ID token's audience
        // (decision 80). Not secret: it is inside every ID token.
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"854486518090-7sc6gs4dmbuo5lgsm9ss9g0p0chedm9a.apps.googleusercontent.com\"",
        )

        // Firebase Cloud Messaging (task 24, decision 109). The project's ids are not secret.
        // The API key is not secret either (it ships in the APK), but it stays out of git:
        // it comes from DINOGYM_FIREBASE_API_KEY or the git-ignored app/google-services.json.
        // Without it the build works and reminders are off.
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"dinogym-3870b\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"854486518090\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseApiKey()}\"")

        // Crashlytics (task 26, decision 120). Its SDK refuses to start without this resource,
        // which the Crashlytics Gradle plugin normally writes. With R8 off there is no mapping
        // file to match, and the plugin then writes this same all-zero id, so we write it here
        // instead of adding the plugin. If minify is ever turned on, use the plugin.
        resValue("string", "com.google.firebase.crashlytics.mapping_file_id", "0".repeat(32))
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
            // The Firebase app registered for io.github.pierorivera1.dinogym.debug.
            buildConfigField("String", "FIREBASE_APP_ID", "\"1:854486518090:android:3a446d3082b1f9cba819b3\"")
            // Staging's HTTP API. Changes if the API is ever recreated (android/README.md).
            buildConfigField("String", "API_BASE_URL", "\"https://ypn4asfnil.execute-api.us-east-1.amazonaws.com\"")
            // Staging's CloudFront domain: the host of its shared links (App Links, decision 99).
            manifestPlaceholders["shareHost"] = "d1q286cdeyw8ks.cloudfront.net"
        }
        release {
            isMinifyEnabled = false
            // Unsigned when no keystore is configured (CI builds on every push, lint), so
            // only the release workflow and the owner's machine produce an installable APK.
            signingConfig = signingConfigs.findByName("release")
            // The Firebase app registered for io.github.pierorivera1.dinogym.
            buildConfigField("String", "FIREBASE_APP_ID", "\"1:854486518090:android:26d3ad82c6fa95cea819b3\"")
            // Prod's HTTP API. Changes if the API is ever recreated (android/README.md).
            buildConfigField("String", "API_BASE_URL", "\"https://cm884h7had.execute-api.us-east-1.amazonaws.com\"")
            // Prod's CloudFront domain. Changes if the distribution is ever recreated (decision 23).
            manifestPlaceholders["shareHost"] = "d1ktti3qymlgmy.cloudfront.net"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME and VERSION_CODE, shown in Settings; the API URL and the
        // Google client ID for sign-in (task 20).
        buildConfig = true
        // The Crashlytics mapping file id above.
        resValues = true
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

    // Sign-in (task 20): the generated API client over OkHttp, and Google through Credential Manager.
    implementation(project(":api-client"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    // Sync (task 21): background push and pull.
    implementation(libs.work.runtime)
    // Reminders (task 24): receives the server's data messages and the phone's FCM token.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Crash reports (task 26): starts with Firebase, no code or Gradle plugin (decision 120).
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
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

/**
 * The Firebase Android API key: DINOGYM_FIREBASE_API_KEY (the release workflow sets it from a
 * GitHub secret), else the key in app/google-services.json (git-ignored, downloaded from the
 * Firebase console), else empty, which leaves reminders off in that build.
 */
fun firebaseApiKey(): String {
    System.getenv("DINOGYM_FIREBASE_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
    val file = file("google-services.json")
    if (!file.exists()) return ""
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parse(file) as Map<String, Any?>
    val clients = json["client"] as? List<Map<String, Any?>> ?: return ""
    val keys = clients.flatMap { it["api_key"] as? List<Map<String, Any?>> ?: emptyList() }
    return keys.firstNotNullOfOrNull { it["current_key"] as? String }.orEmpty()
}
