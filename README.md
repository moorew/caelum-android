# Caelum

> _Caelum_ — Latin for "the heavens" — and the name of a small southern constellation. The four-star constellation glyph is the launcher icon.

An Android companion app for the [Allsky Camera Project](https://github.com/AllskyTeam/allsky), providing a convenient way to monitor your Allsky camera, view weather data, and access historical captures.

> **Renamed in 3.0.0.** This project was previously released as *Allsky Companion App*. The package id (`de.astronarren.allsky`) is unchanged, so existing installs receive 3.0.0 as a normal in-place update — no reinstall, no settings loss.

## Features

- **Live Image View**: Real-time camera feed with 30-second refresh
- **Moon Phase Display**: Current moon phase with illumination percentage
- **Weather Integration**: Local weather forecast using OpenWeather API
- **Media Galleries**:
  - Keograms with full-screen viewer
  - Startrails with full-screen viewer
  - Timelapse videos
- **Dynamic Theme**: Supports light/dark mode and Material You theming
- **Location Services**: Automatic weather data for your location
- **Responsive Design**: Works on phones and tablets

## Acknowledgments

This project is a fork of the [Allsky Companion App](https://github.com/acocalypso/allskyviewer-companion) originally created by [acocalypso](https://github.com/acocalypso). We are grateful for their hard work in establishing the foundation for this app.

## Requirements

- Android 10 (API 29) or higher
- Internet connection
- (Optional) OpenWeather API key for weather data
- URL to your Allsky installation

## Installation

1. Download the latest APK from the [Releases](https://github.com/moorew/caelum-android/releases) page
2. Install on your Android device
3. Follow the setup wizard to configure your Allsky URL and OpenWeather API key

## Setup

### First Launch
The app will guide you through a setup process where you'll need to:
1. Enter your Allsky installation URL
2. (Optional) Add your OpenWeather API key
3. Grant location permissions if using weather features

### OpenWeather API Key
To use weather features:
1. Visit [OpenWeather](https://home.openweathermap.org/api_keys)
2. Create a free account
3. Generate an API key
4. Enter the key in the app settings

## Building from Source

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11 or higher
- Android SDK with API 34

### Steps
1. Clone the repository: