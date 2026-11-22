# AGENTS.md — BttrCal

Projektregeln für **BttrCal** (Local-first Android-Kalender mit Liquid Glass, konfigurierbarem Auto-Sync, Widgets & BYOK-KI).

## Build & Test (Linux)

```bash
export JAVA_HOME=/home/manuelblasaditsch/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
export ANDROID_HOME=/home/manuelblasaditsch/Android/sdk

./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Architektur

- `domain/` — reine Kotlin-Modelle und deterministische Engines (`RecurrenceEngine`, `IcsCodec`, `LocalEventParser`, `TimeGridLayoutEngine`, `ScheduleAnalyzer`)
- `data/local/` — SQLite (`BttrCalDatabase` im WAL-Modus) & `PreferencesRepository`
- `data/security/` — Android Keystore AES-256-GCM (`SecretVault`)
- `data/sync/` — `AndroidCalendarSyncAdapter` (Google/Honor/Exchange via CalendarContract), `GoogleApiSyncAdapter`, `CalDavSyncAdapter`, `IcsSubscriptionSyncAdapter`, `SyncOrchestrator`, `CalendarSyncWorker`
- `data/ai/` — `AiClient` (OpenAI, Gemini, Anthropic, Custom OpenAI-compatible) & `AiCalendarService`
- `notifications/` — `ReminderScheduler`, `ReminderReceiver`, `BootReceiver`
- `widget/` — `MonthAgendaWidgetProvider`, `TodayCardWidgetProvider`, `AgendaListWidgetProvider`
- `ui/` — Apple-inspiriertes Designsystem mit Light, Dark und Liquid Glass Mode

## Sicherheit & Datenschutz

- Geheimnisse (API-Keys, Passwörter, OAuth-Tokens) werden ausschließlich im `SecretVault` (Android Keystore) gespeichert und nie geloggt.
- Alle Kalenderdaten liegen lokal in SQLite (local-first).
