import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pocketweibo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketweibo"
        minSdk = 26
        targetSdk = 34
        // versionName: semantic versioning (MAJOR.MINOR.PATCH). Bump when you ship user-facing changes.
        versionName = "3.75.2"
        // versionCode: must increase for every release APK that should upgrade in-place (same signing key).
        versionCode = 227

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // keystore.properties lives in secrets/ (repo-root fallback for legacy setups);
            // storeFile is resolved relative to the properties file's directory.
            // CI regeneration writes secrets/ci-release.keystore + secrets/keystore.properties via
            // .github/workflows/build-apk.yml and scripts/build-pocketweibo-release.sh.
            val candidate = listOf(rootProject.file("secrets/keystore.properties"), rootProject.file("keystore.properties"))
                .firstOrNull { it.exists() } ?: return@create
            val p = Properties()
            candidate.inputStream().use { p.load(it) }
            val storeRel = p.getProperty("storeFile") ?: return@create
            val storeFileProp = File(storeRel)
            val store = if (storeFileProp.isAbsolute) storeFileProp else candidate.parentFile.resolve(storeRel)
            if (!store.exists()) return@create
            storeFile = store
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias") ?: "pocketweibo"
            keyPassword = p.getProperty("keyPassword")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Roadmap P1: explicit lint policy, enforced in CI. Pre-existing issues live in the baseline.
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = false
    }

    buildTypes {
        release {
            // R8 shrinks unused code; ~smaller release APK. Resource shrinking stays off:
            // avatars are resolved via Resources.getIdentifier(name, "drawable", …).
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        // Keep graph on core 1.12: some transitive deps request 1.17 (compileSdk 36 + AGP 8.9+).
        // REVIEW-2 §1: reviewed and kept on purpose (AGENTS.md); remove when moving to compileSdk 35+.
        force("androidx.core:core-ktx:1.12.0", "androidx.core:core:1.12.0")
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    // Roadmap P1: in-memory Room / repository tests on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.core)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
