

\# Sapphire Development Instructions



\## Project

Sapphire is a Fabric client-side translation mod for

Hypixel SkyBlock on Minecraft 26.1.2.



\## Environment

\- Java 25

\- Fabric Loader

\- Fabric API

\- Gradle

\- IntelliJ IDEA



\## Architecture

\- Use NEU-Repo as the source of English item metadata.

\- Use local JSON dictionaries for Chinese translations.

\- Match items by Hypixel SkyBlock internal item ID.

\- Translate item names and lore at display time.

\- Preserve formatting, colors, numbers and styles.

\- Never mutate server-owned item data.

\- Keep translation dictionaries separate from Java code.



\## Development Rules

1\. Inspect existing code before making changes.

2\. Verify Minecraft API signatures before writing Mixins.

3\. Prefer small, testable changes.

4\. Run Gradle build after code changes.

5\. Automatically fix compilation errors.

6\. Add tests for translation logic.

7\. Do not overwrite existing translations.

8\. Do not change unrelated project files.

9\. Never expose Minecraft account credentials.

10\. Explain any unresolved issues.



\## Initial Goal

Produce a working Fabric mod that translates

SkyBlock item names and lore using local JSON files.



