plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.petermathie.vibecheck"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.petermathie.vibecheck"
        minSdk = 23
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.0-beta.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val signingEnvironment = listOf(
        "VIBE_CHECK_BETA_KEYSTORE_PATH",
        "VIBE_CHECK_BETA_STORE_PASSWORD",
        "VIBE_CHECK_BETA_KEY_ALIAS",
        "VIBE_CHECK_BETA_KEY_PASSWORD",
    ).associateWith { providers.environmentVariable(it).orNull }
    val betaSigningConfig = if (signingEnvironment.values.all { !it.isNullOrBlank() }) {
        signingConfigs.create("beta") {
            storeFile = file(signingEnvironment.getValue("VIBE_CHECK_BETA_KEYSTORE_PATH")!!)
            storePassword = signingEnvironment.getValue("VIBE_CHECK_BETA_STORE_PASSWORD")
            keyAlias = signingEnvironment.getValue("VIBE_CHECK_BETA_KEY_ALIAS")
            keyPassword = signingEnvironment.getValue("VIBE_CHECK_BETA_KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SEED_DEMO_DATA", "true")
        }
        release {
            buildConfigField("boolean", "SEED_DEMO_DATA", "false")
            betaSigningConfig?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("docs/examples"))
    sourceSets.getByName("androidTest").assets.srcDir(file("schemas"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.configureEach {
    if (name.endsWith("AndroidTestRuntimeClasspath")) {
        resolutionStrategy.force(
            "org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
        )
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    implementation("androidx.compose.ui:ui:1.9.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.2")
    implementation("androidx.compose.foundation:foundation:1.9.2")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.2")

    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")
    ksp("androidx.room:room-compiler:2.8.1")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.2")
}
