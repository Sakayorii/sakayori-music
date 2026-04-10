<div align="center">

<img src="images/logo.png" alt="SakayoriMusic" width="128" height="128"/>

# SakayoriMusic

**Kotlin Multiplatform & Compose で構築されたモダンなクロスプラットフォーム YouTube Music クライアント**

[![Version](https://img.shields.io/badge/version-2.1.1-blue.svg)](https://github.com/Sakayorii/sakayori-music/releases/latest)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20Linux%20%7C%20macOS-lightgrey.svg)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](../LICENSE)

[ダウンロード](#ダウンロード) · [機能](#機能) · [スクリーンショット](#スクリーンショット) · [ビルド](#ビルド) · [クレジット](#クレジット)

[English](../README.md) · [Tiếng Việt](README.vi.md) · **日本語**

</div>

---

## 概要

SakayoriMusic は、YouTube Music の体験を複数のプラットフォームに提供する無料のオープンソース音楽ストリーミングクライアントです。元々 SimpMusic 1.1.0 から再構築され、モバイルとデスクトップの両方でネイティブサポートを備えた、モダンで高性能、機能豊富な音楽プレーヤーへと進化しました。

## 機能

- **YouTube Music からストリーミング** ライブラリへのフルアクセス
- **クロスプラットフォーム** Android、Windows、Linux、macOS 対応
- **Liquid Glass UI** デスクトップでネイティブ macOS スタイルデザイン
- **歌詞内蔵** リッチシンク歌詞対応
- **Discord Rich Presence** 統合
- **Spotify Canvas** 背景動画
- **ローカルプレイリスト** とオフラインダウンロード
- **スリープタイマー** とクロスフェード
- **多言語対応** (20以上の言語)
- **バックアップと復元**
- **ミニプレーヤー** モード (デスクトップマルチタスク用)
- **Android Auto** と Wear OS 通知サポート
- **広告なし、追跡なし、テレメトリーなし**

## ダウンロード

[Releases ページ](https://github.com/Sakayorii/sakayori-music/releases/latest) から最新版をダウンロードしてください。

| プラットフォーム | フォーマット | 説明 |
|----------------|------------|------|
| **Android** | `.apk` | すべてのデバイス用ユニバーサル APK |
| **Windows** | `.msi` / `.exe` | GUI セットアップウィザード付きインストーラー |
| **Linux** | `.deb` / `.rpm` | Debian/Ubuntu と Fedora/RHEL 用パッケージ |
| **macOS** | `.dmg` | Apple Silicon と Intel ビルド |

### Windows インストール時の注意

インストーラーを初めて実行すると、アプリが Extended Validation 証明書で署名されていないため、Windows SmartScreen が警告を表示することがあります。続行するには:

1. **詳細情報** をクリック
2. **実行** をクリック

これは未署名のオープンソースアプリケーションでは正常な動作です。

## システム要件

- **Android** 8.0 (API 26) 以上
- **Windows** 10 / 11 (64-bit)
- **Linux** Ubuntu 20.04+、Fedora 34+ 以上 (x64)
- **macOS** 11.0 Big Sur 以上 (Intel + Apple Silicon)

## ビルド

### 要件

- JDK 21 以上
- Android SDK 36
- Gradle 8.14+

### ビルドコマンド

```bash
git clone https://github.com/Sakayorii/sakayori-music.git
cd sakayori-music

./gradlew vlcSetup

./gradlew androidApp:assembleRelease

./gradlew composeApp:packageExe composeApp:packageMsi
```

## 技術スタック

- **Kotlin Multiplatform** 共有コード用
- **Jetpack Compose** & **Compose Multiplatform** UI 用
- **Koin** 依存性注入
- **Room** ローカルデータベース
- **Media3 ExoPlayer** Android 再生
- **VLC** (vlcj) デスクトップ再生
- **Ktor** ネットワーキング
- **Coil** 画像読み込み
- **KCEF** デスクトップ WebView
- **Sentry** クラッシュレポート

## クレジット

- **元プロジェクト:** [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev
- **メンテナー:** [Sakayorii](https://github.com/Sakayorii)

## ライセンス

Copyright © 2026 **Sakayori Studio**.

[MIT ライセンス](../LICENSE) の下でライセンスされています — 自由に使用、変更、配布、派生作品の作成が可能です。許可は不要です。

## 免責事項

SakayoriMusic は Google、YouTube、Spotify と提携、承認、または後援されていません。アプリ上に表示されるすべての商標、サービスマーク、商号、製品名およびロゴは、それぞれの所有者の財産です。
