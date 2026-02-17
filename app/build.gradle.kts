plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.studysync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.studysync"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }

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
}

dependencies {
    // AndroidX and Material
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Firebase (using BOM for version management)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)

    // Google Sign-In
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.15.1")

    // Agora RTC
    implementation("io.agora.rtc:full-sdk:4.6.1")

    // Permissions
    implementation("com.karumi:dexter:6.2.3")

    // ✅ CLOUDINARY - ADD THESE
    implementation("com.cloudinary:cloudinary-android:2.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // RecyclerView (if not already in libs)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
