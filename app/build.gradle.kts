plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vortessence.mahjong"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Own ID so Dark Mahjong installs alongside upstream; the Kotlin namespace stays
        // upstream's to keep merges from PcmciaKai/mahjong_solitaire easy
        applicationId = "com.formlessform.darkmahjong"
        // Android 13: AGSL RuntimeShader for the generated backgrounds and tile effects
        minSdk = 33
        targetSdk = 37
        versionCode = 18
        versionName = "1.4.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Test builds for the phone (-PperfHooks=true, as tools/deploy-to-phone.sh builds them)
        // can be told over adb to open a fixed scene for measuring; Play builds never can
        buildConfigField("boolean", "PERF_HOOKS", (findProperty("perfHooks") == "true").toString())
    }

    // Two builds: "play" for Google Play also asks Play whether a newer version is out (Google's
    // in-app update library, which is closed source); "foss", for F-Droid and anyone building
    // from source, has none of it. They differ only in AppUpdates
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("foss") { dimension = "store" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs (for F-Droid)
        includeInApk = false
        // Kept in the App Bundle, which only Google Play receives (encrypted), so Play Console can
        // flag outdated or vulnerable libraries
        includeInBundle = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric screenshot tests render real resources with Android's own graphics stack
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "3g"
                // Robolectric reaches into FileDescriptor internals, which newer JDKs lock down
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    "playImplementation"(libs.play.app.update)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
