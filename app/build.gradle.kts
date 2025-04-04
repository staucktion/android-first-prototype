import java.util.Properties
import java.io.FileInputStream

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.staucktion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.staucktion"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.6.1-test"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(rootProject.file("keystore/staucktion_debug.keystore"))
            storePassword = "password"
            keyAlias = "staucktion_debug"
            keyPassword = "password"
        }

        create("release") {
            storeFile = file(rootProject.file("keystore/staucktion_release.jks"))
            storePassword = keystoreProperties["releaseStorePassword"] as String
            keyAlias = keystoreProperties["releaseKeyAlias"] as String
            keyPassword = keystoreProperties["releaseKeyPassword"] as String
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }

        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            // isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Add Retrofit using Version Catalog
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.timber)
    // Add the ScalarsConverterFactory dependency and import it:
    implementation ("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.google.android.gms:play-services-auth:20.5.0")
    implementation ("com.google.android.material:material:1.8.0")
    implementation ("com.google.android.gms:play-services-auth:20.5.0")
    implementation ("com.github.bumptech.glide:glide:4.13.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.13.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")








}

tasks.register("printVersionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}
