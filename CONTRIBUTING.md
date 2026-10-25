# Contributing

Thanks for your interest! OpenCarbu is an AGPLv3 Android app (minSdk 29, Kotlin, Jetpack Compose).

- Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first (a French version, `ARCHITECTURE.fr.md`, is kept in sync).
- Code comments are in English. User-visible text goes in `res/values/strings.xml` **and** `res/values-fr/strings.xml`
  (same keys in both).
- Documentation changes must be made in English and French together (`README`, `docs/`).
- Pure logic lives in `domain/` and is unit-tested (`./gradlew test`); please add a test with each change there.
- Keep requests and energy use low: no new network calls, background work or permissions without discussing it first.
- Open an issue before a large change.
