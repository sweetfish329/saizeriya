# Phase 0: プロジェクト基盤構築

## 概要

Android アプリケーションプロジェクトの初期セットアップを行う。
Gradle（Kotlin DSL）によるビルド設定、依存関係の定義、パッケージ構造の作成、AndroidManifest の設定を完了させる。

## 前提条件

- Android Studio Ladybug（2024.2+）インストール済み
- JDK 17+ インストール済み
- Galaxy S24 実機 または Android Emulator（API 34+）

## 想定期間: 2日

---

## 実装タスク一覧

- [ ] Task 0-1: Android プロジェクト新規作成
- [ ] Task 0-2: `build.gradle.kts`（プロジェクトレベル）設定
- [ ] Task 0-3: `build.gradle.kts`（appレベル）設定
- [ ] Task 0-4: パッケージ構造の作成
- [ ] Task 0-5: `AndroidManifest.xml` 基本設定
- [ ] Task 0-6: Application クラス作成
- [ ] Task 0-7: ビルド確認

---

## Task 0-1: Android プロジェクト新規作成

Android Studio → New Project → **Empty Compose Activity**

| 項目 | 値 |
|------|-----|
| Name | SaizeriyaOrder |
| Package name | com.example.saizeriya |
| Language | Kotlin |
| Minimum SDK | API 34 (Android 14) |
| Build configuration language | Kotlin DSL |

> **注意**: Minimum SDK は API 34 必須（Health Connect 要件）

---

## Task 0-2: バージョンカタログ設定

### ファイル: `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
ktor = "3.0.3"
kotlinxSerialization = "1.7.3"
kotlinxCoroutines = "1.9.0"
healthConnect = "1.1.0-alpha10"
navigationCompose = "2.8.5"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
androidx-health-connect = { group = "androidx.health.connect", name = "connect-client", version.ref = "healthConnect" }
junit = { group = "junit", name = "junit", version = "4.13.2" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

---

## Task 0-3: `app/build.gradle.kts` 設定

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.saizeriya"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.saizeriya"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    // Android基本
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Compose
    implementation(platform(libs.androidx.compose.bom))
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
    implementation(libs.androidx.health.connect)
    // テスト
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
```

> **補足**: LiteRT-LM 依存関係は Phase 3 で追加する

---

## Task 0-4: パッケージ構造

`app/src/main/kotlin/com/example/saizeriya/` 配下に以下を作成:

```
├── data/model/       # MenuItem, ContextData, OrderRequest, LlmResponse
├── data/repository/  # MenuRepository, OrderRepository
├── context/          # HealthDataProvider, WeatherProvider, GmailProvider, ContextCollector
├── llm/              # LlmEngine, PromptBuilder, ResponseParser
├── order/            # SaizeriyaClient, OrderExecutor, OrderPipeline
└── ui/
    ├── theme/        # Theme.kt
    ├── screen/       # HomeScreen, OrderScreen, ResultScreen
    └── viewmodel/    # OrderViewModel
```

---

## Task 0-5: AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />
    <uses-permission android:name="android.permission.health.READ_SLEEP" />

    <application
        android:name=".SaizeriyaApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.SaizeriyaOrder">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- サイゼリヤQR URLディープリンク -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:pathPrefix="/saizeriya3" />
            </intent-filter>
        </activity>
        <!-- Health Connect パーミッション用 -->
        <activity-alias
            android:name="ViewPermissionUsageActivity"
            android:exported="true"
            android:targetActivity=".MainActivity"
            android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
            <intent-filter>
                <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
                <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
            </intent-filter>
        </activity-alias>
    </application>
</manifest>
```

---

## Task 0-6: Application クラス

### `SaizeriyaApp.kt`

```kotlin
package com.example.saizeriya

import android.app.Application

class SaizeriyaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase 3 で LiteRT-LM Engine 初期化を追加
    }
}
```

---

## Task 0-7: ビルド確認

```bash
./gradlew build
```

**期待結果**: `BUILD SUCCESSFUL`

| 問題 | 対処法 |
|------|--------|
| minSdk エラー | `minSdk = 34` を確認 |
| Ktor 解決エラー | `mavenCentral()` がリポジトリに含まれるか確認 |
| JDK エラー | JDK 17+ を使用しているか確認 |

---

## 完了条件

- [ ] `./gradlew build` 成功
- [ ] エミュレータ/実機でアプリ起動（空のCompose画面が表示）
- [ ] 全依存ライブラリの解決確認
- [ ] パッケージ構造が作成済み

## 次フェーズ

Phase 0 完了後、**Phase 1, 2, 3 を並列開始**可能。
