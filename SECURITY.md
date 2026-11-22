# Security Policy

## Local-First & Privacy Architecture

- **Local Storage:** All calendar events, metadata, and sync logs live locally on-device in SQLite (`WAL` mode).
- **Encrypted Credentials:** BYOK AI API keys, CalDAV passwords, and OAuth tokens are encrypted using AES-256-GCM backed by the Android Keystore (`SecretVault.kt`) and are never logged or sent to any third-party proxy.
- **Direct Connections:** AI requests go directly from your device to the provider you configure (OpenAI, Google Gemini, Anthropic, or your local/self-hosted OpenAI-compatible server).

## Reporting a Vulnerability

If you discover a security vulnerability, please open a private security advisory on GitHub (`Security -> Advisories -> Report a vulnerability`) rather than posting sensitive details publicly before a fix is released.
