import java.util.Properties

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val firebaseConfigured = file("google-services.json").isFile
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")
val localConfig = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val eewRelayUrl = providers.environmentVariable("PLAND_EEW_RELAY_URL").orNull
    ?: localConfig.getProperty("eew.relayUrl", "")

android {
    namespace = "io.github.nevaseme.plandmk1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.nevaseme.plandmk1"
        minSdk = 24
        targetSdk = 37
        versionCode = 21
        versionName = "1.5.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "EEW_FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "EEW_RELAY_URL", "\"${eewRelayUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.nearby)
    implementation(libs.maplibre)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.compose)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// JVM integration tests read these files directly; invalidate cached tests when
// a new fixed-data package is generated (including asset filename changes).
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/assets/kyoto-building-assessment"))
}
