# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| Latest  | Yes                |
| Older   | No                 |

## Reporting a Vulnerability

If you discover a security vulnerability in Pocket Assistant, please report it responsibly:

1. **Do not** open a public GitHub issue for security vulnerabilities.
2. Instead, email **charles@pocketassistant.app** or use [GitHub's private vulnerability reporting](https://github.com/chartmann1590/Pocket-Assistant/security/advisories/new).
3. Include a description of the vulnerability, steps to reproduce, and any potential impact.
4. You will receive an acknowledgment within 48 hours.

## Scope

This policy covers the Pocket Assistant Android application and its source code. Third-party dependencies (ML Kit, LiteRT-LM, MediaPipe, etc.) should be reported to their respective maintainers.

## Design Principles

- All OCR and AI inference runs on-device by default.
- No user data is sent to any cloud backend unless the user explicitly configures an Ollama server.
- No telemetry, analytics, or tracking.
- Sensitive data (API tokens, Ollama URLs) is stored in Android DataStore on the device and never transmitted.
