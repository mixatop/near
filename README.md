# Near Action — Fabric 1.21.11

A client-side Fabric mod that periodically sends `/near` and runs a command you configure when a matching positive response is received.

## Important

- Use only on servers where automated `/near` checks and command automation are allowed.
- The `/near` response format varies between servers. The default detection patterns are generic and may need adjustment for your server.
- The mod is **off by default**. Press **J** in-game to start/stop monitoring.
- It sends `/near` every 10 seconds while monitoring is enabled. The interval is fixed in code and is not a config option.
- The configured command runs only once while nearby players continue to be reported. A message matching the negative-response pattern re-arms the trigger.

## Configuration

After launching Minecraft once, edit:

`\.minecraft\config\near-action.json`

Example:

```json
{
  "nearCommand": "near",
  "commandToRun": "YOUR_COMMAND_HERE",
  "positiveResponseRegex": "(?i).*(?:nearby\\s+players?\\s*[:=-]\\s*\\S+|players?\\s+(?:near|within)\\b.*|found\\s+\\d+\\s+players?\\b.*|рядом\\s*[:=-]\\s*\\S+|игроки\\s+(?:рядом|поблизости).*).*",
  "negativeResponseRegex": "(?i).*(?:no players|nobody|none nearby|нет игроков|никого рядом|никого поблизости|не найдено|не обнаружено).*"
}
```

Set `commandToRun` to the command you want, with or without the leading `/`. Keep the JSON valid. The mod does not support a `{player}` placeholder because `/near` output differs by server.

If your server's response doesn't match the default patterns, copy the exact text shown after running `/near` (one example with a nearby player and one with nobody nearby) so the patterns can be adjusted.

## Build

Requires Java 21 and Gradle. From this folder run:

- Windows: `gradle build`
- macOS/Linux: `gradle build`

The remapped mod JAR will be created in `build/libs/`.

This source project was prepared in an environment without network access, so the JAR has not been compiled or tested here.
