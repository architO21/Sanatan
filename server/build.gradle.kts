plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "com.example.networkio"
version = "1.0.0"
application {
    mainClass.set("com.example.networkio.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation("io.ktor:ktor-server-websockets:${libs.versions.ktor}")
    implementation("io.ktor:ktor-server-content-negotiation:${libs.versions.ktor}")
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}