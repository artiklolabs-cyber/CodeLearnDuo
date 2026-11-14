# CodeLearn Duo

A Duolingo-style Python and Kotlin learning app with a built-in mobile code editor.
Built with Kotlin 2.4.10, Jetpack Compose (Material 3), AGP 9.2.1 and Gradle 9.6.1.

* **Python** runs 100% offline on-device through Chaquopy 17.2.0 (Python 3.13).
* **Kotlin** runs on a remote Judge0 CE sandbox (default `https://ce.judge0.com`). The Kotlin language id is discovered from `/languages`; you can override the endpoint, auth headers and language id in **More > Kotlin Compiler API Settings**.

## Build locally (Android Studio)

1. Install Android Studio with JDK 17 and the Android SDK for API 37.
2. Install **Python 3.13** and make sure `python3.13` (Windows: `py -3.13`) is on your PATH. Chaquopy needs it at build time.
3. Open this folder in Android Studio. It will use `gradle/wrapper/gradle-wrapper.properties` to fetch Gradle 9.6.1.
4. Run the `app` configuration on an arm64 or x86_64 device or emulator.

`gradle.properties` disables the Gradle configuration cache because Chaquopy does not support it.

## Build an APK with GitHub Actions (no wrapper jar required)

1. Create a GitHub repository and push this project to `main` or `master`.
2. Open the **Actions** tab. The *Android Build* workflow starts automatically (or run it with *Run workflow*).
3. When it finishes, download the **CodeLearnDuo-debug-apk** artifact and install `app-debug.apk`.

The workflow installs JDK 17, Python 3.13 and Gradle 9.6.1 itself, so no `gradle-wrapper.jar` is needed.

## Package the project

```
python3 package_project.py
```

This writes `CodeLearnDuo.zip` (dotfiles such as `.github/workflows/android-build.yml` and `.gitignore` included).

## Project layout

* `app/src/main/python/py_runner.py` - Python execution (stdin/stdout capture, traceback formatting)
* `app/src/main/assets/courses/*.json` - lessons, quizzes, examples and challenges
* `app/src/main/java/com/codelearn/duo/` - Compose UI, view model, executors, workspace storage
