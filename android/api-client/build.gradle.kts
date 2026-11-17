import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Ktor client generated from the API contract, api/openapi.yaml (decision 60).
// The generated code lives in build/ and is never edited or committed: change the
// contract and rebuild. A plain JVM module, so its tests run without Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

val contract = rootProject.layout.projectDirectory.file("../api/openapi.yaml")
val generatedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-ktor")
    inputSpec.set(contract.asFile.path)
    outputDir.set(generatedDir)
    packageName.set("io.github.pierorivera1.dinogym.api")
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    generateApiTests.set(false)
    generateModelTests.set(false)
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "omitGradleWrapper" to "true",
            "omitGradlePluginVersions" to "true",
            "sourceFolder" to "src/main/kotlin",
            // SyncTable.WEIGH_IN, like the app's own enums.
            "enumPropertyNaming" to "UPPERCASE",
        ),
    )
}

// Regenerate when the contract changes, and compile what was generated.
tasks.openApiGenerate {
    inputs.file(contract).withPathSensitivity(PathSensitivity.RELATIVE)
}
sourceSets.main {
    kotlin.srcDir(tasks.openApiGenerate.map { generatedDir.get().dir("src/main/kotlin") })
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
