# Contributing to Tessera VPN Bot

Thanks for your interest in improving Tessera! This document covers the basics.

## Development setup

- JDK 21 (the build enforces a Java 21 toolchain).
- Docker + Docker Compose (for the local Postgres + bot stack).
- Lombok: enable annotation processing in your IDE (IntelliJ: *Settings -> Build -> Compiler -> Annotation Processors -> Enable*). Install the Lombok plugin.

```bash
cp .env.example .env      # fill in the values
./gradlew build           # compile + run tests
docker compose up -d --build
```

## Code style

- `.editorconfig` defines indentation and line endings; keep it green.
- 4-space indentation for Java, 120-char soft line limit.
- Prefer Lombok (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter`, `@Builder`) over hand-written boilerplate.
- Keep the layering intact: `tessera-telegram -> tessera-services -> {tessera-remnawave, tessera-db}`.
  The Telegram layer must not talk to the Remnawave API or JDBC directly.

## Internationalization (i18n)

- User-facing strings live in `tessera-i18n/src/main/resources/i18n/messages[_xx].properties`.
- `messages.properties` (English) is the base bundle and the fallback for every key.
- When you add a key, add it to the English base first, then translate into the other bundles.
- Never hard-code user-facing text in Java — always go through `Messages.t(lang, key, args...)`.

## Commits & PRs

- Use clear, imperative commit messages ("Add language selector", not "added stuff").
- Keep PRs focused; include a short description of what and why.
- Ensure `./gradlew build` passes before opening a PR.

## Adding a language

1. Add an enum constant to `com.tessera.i18n.Lang` (code + display name + flag emoji).
2. Create `messages_<code>.properties` with the user-facing keys.
3. That's it — the language selector and fallback wiring pick it up automatically.
