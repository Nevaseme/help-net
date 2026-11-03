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
val cloudSyncUrl = providers.environmentVariable("PLAND_CLOUD_SYNC_URL").orNull
    ?: localConfig.getProperty("cloud.syncUrl", "https://tasukeai-sync.tomodachi-takumi.workers.dev")

android {
    namespace = "io.github.nevaseme.plandmk1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.nevaseme.plandmk1"
        minSdk = 24
        targetSdk = 37
        versionCode = 70
        versionName = "1.5.57"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "EEW_FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "EEW_RELAY_URL", "\"${eewRelayUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "CLOUD_SYNC_URL", "\"${cloudSyncUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
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
    sourceSets.getByName("test").resources.srcDir("$projectDir/../../shared")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/tsunamiAssets").get().asFile)
    // Retain the historical source for its JVM parser test, but do not distribute a rejected result.
    androidResources {
        // Preserve AGP's default ignores when adding the historical asset exclusion.
        if (ignoreAssetsPatterns.isEmpty()) ignoreAssetsPatterns.addAll(
            listOf("!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~")
        )
        ignoreAssetsPatterns.add("tsunami-atami.json")
    }
}

// Package the browser's display adapter and reviewed 0..35m bundle; no live solver is shipped here.
val syncTsunamiAssets = tasks.register<Sync>("syncTsunamiAssets") {
    from(rootProject.file("../lab/web")) {
        include("tsunami-precomputed.mjs", "tsunami-precomputed-atami.json")
    }
    into(layout.buildDirectory.dir("generated/tsunamiAssets/tsunami-runtime"))
}
tasks.named("preBuild").configure { dependsOn(syncTsunamiAssets) }

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
    inputs.file(layout.projectDirectory.file("src/main/assets/tsunami-atami.json"))
}

