plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.studysync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.studysync"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
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

    // AndroidX
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Circle Image
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Agora Video SDK
    implementation("io.agora.rtc:full-sdk:4.6.1")
// Cloudinary Android SDK
    implementation("com.cloudinary:cloudinary-android:2.5.0")

// Required networking library
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Dexter Permissions
    implementation("com.karumi:dexter:6.2.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(
        "androidx.test.ext:junit:1.2.1"
    )
    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.6.1"
    )
}
