# Android アプリケーションのビルドとテスト

このドキュメントでは、Galaxy S24向けのKotlinベースAndroidアプリケーションのビルド方法とテストの実行手順について説明します。

## ビルド環境
- Android Studio またはコマンドラインのGradle
- JDK (プロジェクトの設定に準拠)

## ビルドコマンド

ターミナルを開き、プロジェクトのルートディレクトリで以下のコマンドを実行します。

```bash
# クリーンビルド
./gradlew clean

# アプリケーションのビルド (Debug APK の生成)
./gradlew assembleDebug

# インストールと実行 (エミュレータまたは実機が接続されている場合)
./gradlew installDebug
```

## テストの実行

ユニットテストを実行するには、以下のコマンドを使用します。

```bash
# 全てのユニットテストを実行
./gradlew testDebugUnitTest

# 全てのテストを実行
./gradlew test
```
