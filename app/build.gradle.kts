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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //Material
    implementation ("com.google.android.material:material:1.11.0")

    // For Responsive Behaviour
    implementation("com.intuit.sdp:sdp-android:1.1.1")
    implementation("com.intuit.ssp:ssp-android:1.1.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))  // Updated to latest stable version
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation(libs.firebase.storage.ktx)

    // Facebook Login
    implementation("com.facebook.android:facebook-login:16.3.0")

    //Gson for json parsing
    implementation ("com.google.code.gson:gson:2.8.8")
}
