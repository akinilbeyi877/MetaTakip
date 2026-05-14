plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

configurations.all {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
    exclude(group = "org.apache.httpcomponents", module = "httpcore")

    resolutionStrategy {
        force("io.grpc:grpc-okhttp:1.62.2")
        force("io.grpc:grpc-protobuf-lite:1.62.2")
        force("io.grpc:grpc-stub:1.62.2")
        force("io.grpc:grpc-core:1.62.2")
        force("io.grpc:grpc-api:1.62.2")
        force("io.grpc:grpc-context:1.62.2")
    }
}

android {
    namespace = "com.example.metatakip"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.metatakip"
        minSdk = 26
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

    implementation("com.google.firebase:firebase-common")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("io.grpc:grpc-okhttp:1.62.2")
    implementation("io.grpc:grpc-protobuf-lite:1.62.2")
    implementation("io.grpc:grpc-stub:1.62.2")
    implementation("io.grpc:grpc-core:1.62.2")
    implementation("io.grpc:grpc-api:1.62.2")
    implementation("io.grpc:grpc-context:1.62.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(project(":feature_admin"))
    implementation(project(":feature_uruntipi"))
    implementation(project(":feature_data"))
    implementation(project(":feature_order"))
    implementation(project(":feature_customer"))
    implementation(project(":feature_unvan"))
    implementation(project(":feature_firma"))
    implementation(project(":feature_personel"))
    implementation(project(":feature_label"))
    implementation(project(":feature_backup"))
    implementation(project(":permissions"))

    implementation("androidx.multidex:multidex:2.0.1")

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.koushikdutta.async:androidasync:3.1.0")

    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-base:18.3.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // 📊 Apache POI (Excel dosyası okumak için)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.guolindev.permissionx:permissionx:1.7.1")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.25")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")

    implementation("com.github.skydoves:colorpickerview:2.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}