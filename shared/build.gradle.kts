plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    kotlin("plugin.serialization") version "1.9.21"
}

kotlin {
    androidTarget()
    
    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    js {
        browser()
    }
    
    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
            implementation("io.ktor:ktor-client-core:2.3.7")
            implementation("io.ktor:ktor-client-websockets:2.3.7")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:2.3.7")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:2.3.7")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:2.3.7")
        }
        jsMain.dependencies {
            implementation("io.ktor:ktor-client-js:2.3.7")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.example.networkio.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
