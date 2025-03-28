plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.cricketApp.cric"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cricketApp.cric"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        // Add this line to handle Kotlin version compatibility
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    viewBinding {
        enable = true
    }
}

dependencies {
    // Default Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    androidTestImplementation(libs.androidx.ui.test.android)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.core)
    implementation(libs.vision.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //Material
    implementation ("com.google.android.material:material:1.11.0")

    // For Responsive Behaviour
    implementation("com.intuit.sdp:sdp-android:1.1.1")
    implementation("com.intuit.ssp:ssp-android:1.1.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation ("com.google.firebase:firebase-firestore")
    implementation ("com.google.firebase:firebase-storage")
    implementation ("com.google.android.gms:play-services-auth:20.7.0")

    // Facebook Login
    implementation("com.facebook.android:facebook-login:16.3.0")

    //Google Login
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    //Gson for json parsing
    implementation ("com.google.code.gson:gson:2.8.8")

    //Material Dependencies
    implementation ("com.google.android.material:material:1.9.0")

    // Navigation Components
    implementation ("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation ("androidx.navigation:navigation-ui-ktx:2.6.0")

    // RecyclerView for displaying video list
    implementation ("androidx.recyclerview:recyclerview:1.2.1")

    // Media3 for playing videos (replacing ExoPlayer)
    implementation ("com.google.android.exoplayer:exoplayer:2.18.7")
    implementation ("androidx.media3:media3-exoplayer:1.2.1")
    implementation ("androidx.media3:media3-ui:1.2.1")
    implementation ("androidx.media3:media3-exoplayer-dash:1.2.1")
    implementation ("androidx.media3:media3-exoplayer-hls:1.2.1")

    //Glide for Thumbnail loading
    implementation ("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.1")

    // Retrofit for network calls
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")

    // ViewModel and LiveData
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation ("androidx.core:core-ktx:1.12.0")

    // ML Kit for Image Labeling
    implementation("com.google.mlkit:image-labeling:17.0.7")

    // ML Kit for Face Detection
    implementation ("com.google.mlkit:face-detection:16.1.5")

    // ML Kit Object Detection (optional, but useful for additional checks)
    implementation ("com.google.mlkit:object-detection:17.0.0")

    // Task API
    implementation ("com.google.android.gms:play-services-tasks:18.0.2")

    // OkHttp for network requests
    implementation ("com.squareup.okhttp3:okhttp:4.10.0")

    // Gson for JSON parsing
    implementation ("com.google.code.gson:gson:2.9.0")

    // Coroutines for async work
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    //Notification Firebase Cloud Messaging
    implementation ("com.google.firebase:firebase-messaging:23.2.1")
}