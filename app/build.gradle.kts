plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.imageviewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.imageviewer"
        minSdk = 29
        targetSdk = 34

        // Version contract — see docs/release.md for the full picture:
        //   `baseVersion` is the manual major.minor — bump it in this file only when
        //   you intend a meaningful release. Build counter (the patch component) is
        //   sourced from the VERSION_CODE env var, which CI sets to github.run_number.
        //   Local builds get build=1 so versionName is stable for development.
        val baseVersion = "0.1"
        val build = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        val fullVersion = "$baseVersion.$build"

        versionCode = build
        versionName = fullVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "VERSION_NAME", "\"$fullVersion\"")
        buildConfigField("String", "GITHUB_REPO", "\"alexey-a-abramov/pic-path\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Copy the assembled debug APK to a shared "builds" directory so other tools
// (Termux side-load, file managers, etc.) can pick it up without digging into
// app/build/outputs/. Override the destination with -PbuildsDir=/some/path.
val buildsCopyDir: String =
    (project.findProperty("buildsDir") as? String) ?: "/storage/emulated/0/builds"

val copyApkToBuilds = tasks.register<Copy>("copyApkToBuilds") {
    description = "Copies the debug APK into $buildsCopyDir/pic-path.apk"
    group = "distribution"
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(buildsCopyDir)
    rename { "pic-path.apk" }
    doFirst { file(buildsCopyDir).mkdirs() }
}

// AGP registers assembleDebug after this script's top-level runs; wire the
// finalizer (and the dependency) in afterEvaluate when the task exists.
afterEvaluate {
    tasks.named("assembleDebug") {
        finalizedBy(copyApkToBuilds)
    }
    copyApkToBuilds.configure {
        dependsOn("assembleDebug")
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-extended")

    // Compose Activity
    implementation("androidx.activity:activity-compose:1.8.1")

    // Lifecycle ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Paging 3 + Compose integration
    val pagingVersion = "3.3.2"
    implementation("androidx.paging:paging-runtime-ktx:$pagingVersion")
    implementation("androidx.paging:paging-compose:3.3.2")

    // Coil for Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Pull to Refresh
    implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")

    // OkHttp for GitHub API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Core KTX
    implementation("androidx.core:core-ktx:1.12.0")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // AppCompat for Locale management
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
