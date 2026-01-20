import de.undercouch.gradle.tasks.download.Download

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.de.undercouch.download)
}

val assetDir = "$projectDir/src/main/assets"
val gestureTaskUrl = "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task"
val faceTaskUrl = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"

android {
    namespace = "com.example.selfiedemo"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.selfiedemo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mediapipe.vision)
}

tasks.register<Download>("downloadGestureTaskAsset") {
    src(gestureTaskUrl)
    dest("$assetDir/gesture_recognizer.task")
    overwrite(false)
}

tasks.register<Download>("downloadFaceTaskAsset") {
    src(faceTaskUrl)
    dest("$assetDir/face_landmarker.task")
    overwrite(false)
}

tasks.named("preBuild") {
    dependsOn("downloadGestureTaskAsset", "downloadFaceTaskAsset")
}