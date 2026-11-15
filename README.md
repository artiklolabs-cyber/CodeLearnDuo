# CodeLearn Duo

A Duolingo-style Python and Kotlin learning app with a built-in mobile code editor.
Built with Kotlin 2.4.10, Jetpack Compose (Material 3), AGP 9.2.1 and Gradle 9.6.1.

* **Python** runs 100% offline on-device through Chaquopy 17.2.0 (Python 3.13) with **NumPy, Matplotlib, Pandas, SymPy and Pillow** installed via `pip { }` in `app/build.gradle.kts` (SciPy is intentionally not included). Charts from `plt.show()` and images from Pillow's `img.show()` appear as images in the Editor console and challenge output.
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

## Curriculum modules

The base courses live in `app/src/main/assets/courses/python_basics.json` and `kotlin_basics.json`.
Extra content is loaded from `app/src/main/assets/courses/modules/*.json`; each module declares a `course`, an `order`, and any of `chapters`, `quiz`, `examples` and `challenges`. New modules are picked up automatically.

* `python_structures.json` - chapters 7-12: nested data, sets/tuples, comprehensions, generators, `*args`/`**kwargs`, decorators, exceptions, context managers, OOP, dataclasses, files, JSON, CSV
* `python_numpy.json`, `python_matplotlib.json`, `python_pandas.json`, `python_sympy.json`, `python_pillow.json` - chapters 13-17 with examples, example-based quizzes and challenges
* `kotlin_advanced.json` - chapters 7-14: null safety, ranges/`when`, collection operations and `Sequence`, extensions and lambdas, interfaces/objects/data classes, sealed types, scope functions, generics, coroutines and `Flow` concepts

Progress is stored by chapter number and quiz id, so existing saved progress keeps working; modules only add new numbers and ids.

**Kotlin sandbox note:** the Judge0 server picks its own Kotlin version. Runnable Kotlin examples and challenges stick to the standard library and avoid very new APIs, but `sealed interface` (lesson code) needs Kotlin 1.5 or newer, and the coroutines chapter needs `kotlinx.coroutines`, which the plain sandbox does not provide.

**APK size:** the scientific libraries make the debug APK large (well over 100 MB) and the first build slow.

## Project layout

* `app/src/main/python/py_runner.py` - Python execution (stdin/stdout capture, traceback formatting)
* `app/src/main/assets/courses/*.json` - lessons, quizzes, examples and challenges
* `app/src/main/java/com/codelearn/duo/` - Compose UI, view model, executors, workspace storage
