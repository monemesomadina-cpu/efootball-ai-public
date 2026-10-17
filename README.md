# eFootball AI Match Tracker — Android MVP

This is the Android capture/display client starter. It captures the screen only after the user grants Android's MediaProjection permission, then uses on-device ML Kit OCR to try to read visible scoreboard text.

## Important scope

- This starter is **not** the trained eFootball-specific AI model.
- Keep private model weights, training data, credentials, and captured match footage out of this repository.
- The intended later architecture is: Android capture client → authenticated connection to your PC-hosted inference service → results displayed in the app.
- OCR results are estimates and need testing against the actual eFootball screen layout.

## Build

Requirements: Android Studio with Android SDK 34 installed, JDK 17, and internet access for dependency downloads.

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on an Android 7.0+ device (API 24+).
4. Grant screen-capture permission when prompted.
5. Press **Start capture**. Stop capture from the app or Android's system controls.

GitHub Actions builds a debug APK and publishes it as a workflow artifact. You can use a private repository; keep this project private while it is under development.

## Tune the OCR

`ScoreReader.kt` currently scans recognized text for score-like pairs and a match clock. Since OCR can confuse player names, time, and score values, test it with screenshots from your own game and adjust the parsing rules. Do not use these estimates for ranked/competitive decisions without validation.
