# BttrCal

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**BttrCal** ist eine moderne, schnelle, quelloffene **Local-First-Kalender-App für Android** (Kotlin & Jetpack Compose), entwickelt als vollständiger Ersatz für Google Kalender und Honor Kalender.

## Highlights

- **Apple-inspiriertes Design & 3 Modi:** **Hell**, **Dunkel** und **Liquid Glass** (Helles Glas & Dunkles Glas) mit butterweichen Spring-Animationen, einklappbarer Monats-/Wochenleiste und horizontalem **Überblick**-Tagesbalken.
- **6 Kalenderansichten:** Tag, 3 Tage, Woche, Monat, Jahr und chronologische Agenda.
- **Local-First SQLite-Datenbank:** Alle Termine, Serien (RFC 5545 `RRULE`) und Erinnerungen (`AlarmManager` mit Schlummern) laufen lokal auf dem Gerät.
- **Konfigurierbarer Auto-Sync & Multi-Kalender:**
  - Intervall frei wählbar (`Aus`, `15 Min`, `30 Min`, `1 Std`, `2 Std`, `4 Std`, `6 Std`, `12 Std`, `24 Std`), optional nur im WLAN, beim App-Start und nach Änderungen.
  - **Google Kalender, Honor Kalender & Outlook/Exchange** bidirektional über Android `CalendarContract`.
  - **Apple iCloud, Nextcloud, Mailbox.org, Fastmail & beliebiges CalDAV**.
  - **Google Calendar REST API v3**, **ICS/WebCal-Abos** und **`.ics`-Import / -Export**.
- **Bring Your Own Key (BYOK) KI:**
  - OpenAI, Google Gemini, Anthropic Claude oder lokale/eigene Endpunkte (z. B. Ollama).
  - Schlüssel werden ausschließlich im **Android Keystore (AES-256-GCM)** gespeichert.
  - Inklusive **100 % Offline-Fallback** für Smart Quick-Add, Tages-/Wochen-Briefings und freie Zeitfenster.
- **4 Homescreen-Widgets (Light, Dark & Liquid Glass):**
  - **Split Glas (4×2 · Apple-/Honor-Stil):** Mini-Monat mit Korallen-Heute-Kreis links & bis zu 3 Glas-Terminkarten rechts
  - **Monat & Termine (4×4)** mit Blättern und Schnell-Hinzufügen
  - **Heute-Karte (4×2)**
  - **Scrollbare Agenda-Liste (4×3)** mit Sync-Button

## Download & Installation

Signierte Release-APKs findest du unter [GitHub Releases](https://github.com/mixxermannipro/BttrCal/releases).

## Selbst bauen & testen

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Lizenz

Veröffentlicht unter der [MIT License](LICENSE).
