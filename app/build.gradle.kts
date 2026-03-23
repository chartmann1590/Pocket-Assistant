import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localProps = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
val adsEnabled = localProps.getProperty("ADS_ENABLED", "false").toBoolean()
val admobAppId = localProps.getProperty("ADMOB_APP_ID", "")
val admobBannerId = localProps.getProperty("ADMOB_BANNER_ID", "")
val admobInterstitialId = localProps.getProperty("ADMOB_INTERSTITIAL_ID", "")
val admobRewardedId = localProps.getProperty("ADMOB_REWARDED_ID", "")

android {
    namespace = "com.charles.pocketassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.charles.pocketassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.charles.pocketassistant.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("boolean", "ADS_ENABLED", "$adsEnabled")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
        manifestPlaceholders["admobAppId"] = if (adsEnabled && admobAppId.isNotBlank()) admobAppId else "ca-app-pub-3940256099942544~3347511713"
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
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.google.material)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.hilt)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)

    implementation(libs.coil.compose)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.entity.extraction)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.smart.reply)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.digital.ink)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mediapipe.tasks.text)
    implementation(libs.google.aicore)
    implementation(libs.google.gms.ads)
    implementation(files("libs/litertlm-android-0.8.0-classes.jar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}
