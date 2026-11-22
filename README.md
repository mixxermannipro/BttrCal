# BttrCal

**BttrCal** ist eine moderne, schnelle Local-First-Kalender-App für Android (entwickelt u. a. für Honor Magic 9 Pro Max) als vollwertiger Ersatz für Google Kalender und Honor Kalender.

## Highlights

- **Apple-inspiriertes Design & drei Modi**: **Light Mode**, **Dark Mode** und **Liquid Glass Mode** (Light Glass & Dark Glass) mit butterweichen Spring-Animationen.
- **6 Ansichten**: Tag, 3-Tage, Woche, Monat, Jahr und chronologische Agenda.
- **Local-First Datenbank**: Alle Termine, Serien (RFC 5545 `RRULE`), Erinnerungen und Einstellungen liegen lokal in SQLite (WAL) – sofort nutzbar ohne Cloud-Zwang.
- **Konfigurierbarer Auto-Sync**: Einstellbares Intervall (`Aus`, `15 Min`, `30 Min`, `1 Std`, `2 Std`, `4 Std`, `6 Std`, `12 Std`, `24 Std`), optional nur im WLAN, beim App-Start und nach Änderungen.
- **Multi-Kalender-Integration**:
  - **Google Kalender & Honor/System-Kalender** bidirektional über Android `CalendarContract`
  - **Direkte Google Calendar REST API v3** (optional mit OAuth-Token)
  - **CalDAV** (Nextcloud, iCloud, Mailbox.org, Fastmail, Radicale, Baikal)
  - **ICS / WebCal-Abos** sowie lokaler `.ics`-Import & -Export
- **Bring Your Own Key (BYOK) KI**:
  - OpenAI, Google Gemini, Anthropic Claude oder beliebige OpenAI-kompatible Endpunkte (z. B. Ollama, OpenRouter, Groq)
  - API-Schlüssel sicher im Android Keystore (AES-256-GCM) verschlüsselt
  - Smart Quick-Add, Tages-/Wochen-Briefing, Text-zu-Termin-Extraktor und freie Zeitfenster (mit 100 % Offline-Fallback)
- **3 Homescreen-Widgets** (Honor-inspiriert & Liquid Glass):
  - **Monat + Agenda (4x4)** mit Blättern und Schnell-Hinzufügen
  - **Heute-Karte (4x2)**
  - **Scrollbare Agenda-Liste (4x3)**

## Bauen & Testen

```bash
export JAVA_HOME=/home/manuelblasaditsch/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
export ANDROID_HOME=/home/manuelblasaditsch/Android/sdk

./gradlew testDebugUnitTest
./gradlew assembleDebug
```
