<div align="center">

<img src="images/logo.png" alt="SakayoriMusic" width="128" height="128"/>

# SakayoriMusic

**Trình phát YouTube Music đa nền tảng hiện đại, xây dựng bằng Kotlin Multiplatform & Compose**

[![Version](https://img.shields.io/badge/version-2.1.1-blue.svg)](https://github.com/Sakayorii/sakayori-music/releases/latest)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20Linux%20%7C%20macOS-lightgrey.svg)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](../LICENSE)

[Tải xuống](#tải-xuống) · [Tính năng](#tính-năng) · [Ảnh chụp](#ảnh-chụp-màn-hình) · [Build](#build) · [Credits](#credits)

[English](../README.md) · **Tiếng Việt** · [日本語](README.ja.md)

</div>

---

## Giới thiệu

SakayoriMusic là trình phát nhạc miễn phí và mã nguồn mở mang lại trải nghiệm YouTube Music trên nhiều nền tảng. Ban đầu được rebuild từ SimpMusic 1.1.0, dự án đã phát triển thành một music player hiện đại, hiệu năng cao với hỗ trợ native cho cả mobile và desktop.

## Tính năng

- **Stream nhạc** từ YouTube Music với truy cập đầy đủ thư viện
- **Đa nền tảng** Android, Windows, Linux và macOS
- **Liquid Glass UI** với thiết kế macOS-style trên desktop
- **Lyrics tích hợp** với rich-sync hỗ trợ word-by-word
- **Discord Rich Presence** integration
- **Spotify Canvas** background videos
- **Local playlists** và tải xuống nghe offline
- **Sleep timer** và crossfade
- **Đa ngôn ngữ** (20+ ngôn ngữ)
- **Backup & restore** dữ liệu của bạn
- **Mini player** cho desktop multitasking
- **Android Auto** và Wear OS notifications
- **Không quảng cáo, không tracking, không telemetry**

## Tải xuống

Tải bản mới nhất từ trang [Releases](https://github.com/Sakayorii/sakayori-music/releases/latest).

| Nền tảng | Định dạng | Mô tả |
|----------|-----------|-------|
| **Android** | `.apk` | APK universal cho mọi thiết bị |
| **Windows** | `.msi` / `.exe` | Installer với GUI setup wizard |
| **Linux** | `.deb` / `.rpm` | Gói cho Debian/Ubuntu và Fedora/RHEL |
| **macOS** | `.dmg` | Build cho Apple Silicon và Intel |

### Lưu ý cài đặt Windows

Khi chạy installer lần đầu, Windows SmartScreen có thể hiển thị cảnh báo vì app chưa được sign bằng EV certificate. Để tiếp tục:

1. Click **More info**
2. Click **Run anyway**

Đây là điều bình thường cho ứng dụng mã nguồn mở chưa sign.

## Yêu cầu hệ thống

- **Android** 8.0 (API 26) trở lên
- **Windows** 10 / 11 (64-bit)
- **Linux** Ubuntu 20.04+, Fedora 34+ trở lên (x64)
- **macOS** 11.0 Big Sur trở lên (Intel + Apple Silicon)

## Build

### Yêu cầu

- JDK 21 trở lên
- Android SDK 36
- Gradle 8.14+

### Lệnh build

```bash
git clone https://github.com/Sakayorii/sakayori-music.git
cd sakayori-music

./gradlew vlcSetup

./gradlew androidApp:assembleRelease

./gradlew composeApp:packageExe composeApp:packageMsi
```

## Tech Stack

- **Kotlin Multiplatform** cho shared code
- **Jetpack Compose** & **Compose Multiplatform** cho UI
- **Koin** cho dependency injection
- **Room** cho local database
- **Media3 ExoPlayer** cho Android playback
- **VLC** (vlcj) cho desktop playback
- **Ktor** cho networking
- **Coil** cho image loading
- **KCEF** cho desktop WebView
- **Sentry** cho crash reporting

## Credits

- **Project gốc:** [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev
- **Maintained bởi:** [Sakayorii](https://github.com/Sakayorii)

## License

Copyright © 2026 **Sakayori Studio**.

Cấp phép theo [MIT License](../LICENSE) — được tự do sử dụng, sửa đổi, phân phối và xây dựng dựa trên mã nguồn này. Không cần xin phép.

## Disclaimer

SakayoriMusic không liên kết, được tài trợ hoặc xác nhận bởi Google, YouTube hoặc Spotify. Tất cả thương hiệu, nhãn hiệu dịch vụ, tên thương mại, tên sản phẩm và logo xuất hiện trên app là tài sản của chủ sở hữu tương ứng.
