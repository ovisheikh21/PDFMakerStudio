# PDF Maker Studio (`com.pdfmakerstudio.app`)

Production-ready Kotlin & Jetpack Compose (Material 3) Android app integrated with Firebase Auth, Cloud Firestore, and Firebase Cloud Storage.

## Provisioned Firebase Project
- **Project ID**: `chrome-scene-0k4jk`
- **Firestore Database ID**: `ai-studio-d8e4c344-5d7d-4156-80fb-64e9ef155d37`
- **Storage Bucket**: `chrome-scene-0k4jk.firebasestorage.app`
- **Web OAuth Client ID (`default_web_client_id`)**: `715801994005-is6pu5n1h3kfrqpi30i0husgvra4t32m.apps.googleusercontent.com`

## Quick Setup in Android Studio
1. Open the `android-app/` folder in **Android Studio Ladybug / Meerkat**.
2. In **Firebase Console** (`chrome-scene-0k4jk`):
   - **Authentication -> Sign-in method**: Enable **Email/Password** and **Google** providers.
   - **Project Settings -> Your apps**: Add your Android app with package name `com.pdfmakerstudio.app` and your debug/release **SHA-1** fingerprint, then replace `app/google-services.json` if needed (keeps Google Sign-In working on physical devices).
   - **Cloud Storage**: Ensure Cloud Storage is initialized and deploy `storage.rules` (`firebase deploy --only storage`).
3. *(Optional Custom Bengali Font)*: `A4PdfGenerator` uses Android's HarfBuzz + Skia system `NotoSansBengali` shaping automatically. To force a specific bundled font, place `noto_sans_bengali.ttf` and `noto_sans_bengali_bold.ttf` in `app/src/main/res/font/`.
4. Build & Run on Android 8.0+ (API 26+).
