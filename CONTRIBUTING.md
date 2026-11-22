# Contributing to BttrCal

Thank you for contributing to **BttrCal**, an open-source, local-first Android calendar built with Kotlin and Jetpack Compose.

## Prerequisites

- JDK 17
- Android SDK (compileSdk 36, build-tools 36.0.0)

## Local Build & Tests

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Pull Request Guidelines

1. Fork the repository and create a short-lived feature branch (`feat/...` or `fix/...`).
2. Keep changes focused and add/update unit tests in `app/src/test/` for any domain logic changes (`RecurrenceEngine`, `IcsCodec`, `LocalEventParser`, `ScheduleAnalyzer`, `TimeGridLayoutEngine`).
3. Verify `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` pass before opening a pull request.
4. Never commit API keys, keystores, `local.properties`, or personal data.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
