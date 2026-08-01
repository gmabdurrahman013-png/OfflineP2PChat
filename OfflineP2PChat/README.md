name: Android CI Build

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
        working-directory: OfflineP2PChat

      - name: Build debug APK with Gradle
        run: ./gradlew assembleDebug
        working-directory: OfflineP2PChat

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: OfflineP2PChat/app/build/outputs/apk/debug/app-debug.apk
