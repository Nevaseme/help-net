plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.nevaseme.plandprobe"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "io.github.nevaseme.plandprobe"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-calibration"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").kotlin.srcDir(layout.buildDirectory.dir("generated/probeSources").get().asFile)
    sourceSets.getByName("test").kotlin.srcDir(layout.buildDirectory.dir("generated/probeUnitTests").get().asFile)
}

// Reuse only the four measurement classes and their theme. No app DB, network,
// Firebase, map, tsunami assets, Application class, or normal app services.
val syncProbeSources = tasks.register<Sync>("syncProbeSources") {
    from(rootProject.file("app/src/main/java")) {
        include("io/github/nevaseme/plandmk1/enclosure/EnclosureFeatures.kt")
        include("io/github/nevaseme/plandmk1/ui/theme/**")
    }
    from(rootProject.file("app/src/debug/java")) {
        include("io/github/nevaseme/plandmk1/enclosure/**")
    }
    into(layout.buildDirectory.dir("generated/probeSources"))
}
val syncProbeUnitTests = tasks.register<Sync>("syncProbeUnitTests") {
    from(rootProject.file("app/src/test/java")) {
        include("io/github/nevaseme/plandmk1/EnclosureFeaturesTest.kt")
    }
    into(layout.buildDirectory.dir("generated/probeUnitTests"))
}
tasks.named("preBuild").configure { dependsOn(syncProbeSources, syncProbeUnitTests) }

dependencies {
    implementation(libs.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
