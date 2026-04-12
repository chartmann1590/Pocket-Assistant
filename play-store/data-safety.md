# Data safety form — answers

Use these answers when filling out the **App content → Data safety** section
in Google Play Console. They reflect how Pocket Assistant actually behaves
in its default local-first configuration.

## Does your app collect or share any of the required user data types?

**No.**

Pocket Assistant has no backend. All captured text, OCR output, AI summaries,
tasks, and reminders are stored in a local Room database on the user's
device. They are not transmitted to us and not shared with any third party.

## Is all of the user data collected by your app encrypted in transit?

**Not applicable** — no user data leaves the device.

*If the user configures an Ollama server, prompts and context are sent to the
endpoint they chose. That connection uses whatever TLS configuration the
user's server exposes; the app does not proxy, cache, or log it anywhere
we can see.*

## Do you provide a way for users to request that their data be deleted?

**Yes** — users can clear the local database from Settings → Local Data
→ Clear database, or uninstall the app.

## Data types — detailed answers

For each data type the Play Console asks about, the correct answer is
**"Not collected"** with one exception documented below.

| Category                         | Collected by app | Shared with third parties |
|----------------------------------|-----------------:|--------------------------:|
| Personal info                    | No               | No                        |
| Financial info                   | No               | No                        |
| Health and fitness               | No               | No                        |
| Messages                         | No               | No                        |
| Photos and videos                | No               | No                        |
| Audio files                      | No               | No                        |
| Files and docs                   | No               | No                        |
| Calendar                         | No               | No                        |
| Contacts                         | No               | No                        |
| App activity                     | No               | No                        |
| Web browsing                     | No               | No                        |
| App info and performance         | No               | No                        |
| Device or other IDs              | No               | No                        |

**Ollama exception:** If the user opts in to an Ollama server, the request
text is transmitted to that server. Disclose this as "User content → Other
in-app user content: collected, not shared, user-initiated" only if you
treat your own Ollama server as a third party. For the default build,
Ollama is opt-in, user-configured, and not part of the default data flow,
so "Not collected" is the accurate Play Console answer.

## Security practices

- **Data is encrypted in transit:** Not applicable by default. When the
  user configures Ollama, they choose the TLS posture.
- **Users can request that their data be deleted:** Yes. Settings → Local
  Data → Clear database.
- **Committed to the Play Families Policy:** No (app is not targeted at
  children).
- **Independent security review:** No.
