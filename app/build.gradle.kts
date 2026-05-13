plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    androidResources.noCompress.add("litertlm")
    lint.abortOnError = false
    buildToolsVersion = "35.0.0"
    namespace = "com.example.saizeriya"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.saizeriya"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "OPENWEATHER_API_KEY", "\"your_api_key_here\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Android基本
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Compose
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    // Navigation
    implementation(libs.androidx.navigation.compose)
    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Health Connect
    // Temporarily disable Health Connect dependency as it may be causing a resolution error with external-protobuf
    // Health Connect
    implementation(libs.androidx.health.connect)
    // LiteRT-LM
    implementation(libs.litert.lm)
    // テスト
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

tasks.whenTaskAdded {
    if (name.contains("checkDebugAarMetadata")) {
        enabled = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.whenTaskAdded {
    if (name.contains("checkReleaseAarMetadata")) {
        enabled = false
    }
}
