# OpenCarbu

🇫🇷 [Version française](README.fr.md)

An open-source Android app that shows French fuel stations on an interactive map.
Each station is a dot labelled with its price for the selected fuel, coloured by how its price
ranks among all the stations in France: **green** (lowest quarter), **yellow**, **orange**, **red** (highest quarter).

**Status:** early development (v0.21.4). Done: core logic (price scale, colours, data
parsing), data download and local cache, map screen with fuel selector, with unit tests. Also: place names on the map, user location (map opens on a configurable radius around you, 15 km by default). Station sheet on tap (address, distance, fuels with update dates, open in a map app). Menu with Info (data, sources, licences) and Settings pages (with a "restore defaults" button). Station brand and name on the sheet (from a reference file, refreshed every 30 days by default). The distance is told "on the back of a <bird>" (a different bird each time, linked to Wikipedia). Address search (magnifier button under the menu) centres the map on the chosen place (IGN geocoder). Optional road distance (off by default, OSRM). "Nearest stations" button (arrow, under the magnifier; greyed out until a position is known): stations within a configurable radius (30 km by default), by distance, with price and fuel cost of the trip.

## Data

Official French open data, "Prix des carburants en France – Flux instantané v2",
published by the Ministry of Economy on [data.economie.gouv.fr](https://www.data.economie.gouv.fr/explore/assets/prix-des-carburants-en-france-flux-instantane-v2/)
under the Licence Ouverte 2.0. The feed is refreshed about every 10 minutes.

## Build

Runs on Android 10 (API 29) and above.

Requirements: [Android Studio](https://developer.android.com/studio) (it bundles the JDK and Android SDK).

1. Open this folder in Android Studio and let Gradle sync.
2. Run on an emulator or a phone (USB debugging enabled): the **Run** button, or `./gradlew installDebug`.
3. Run the unit tests (no device needed): `./gradlew test`.
4. Try the minified release build on a phone (no release key yet, so it is signed with the debug key; testing only): `./gradlew assembleRelease -PdebugSignRelease`, then install `app/build/outputs/apk/release/app-release.apk`.

Source code: https://github.com/dzekdrix/opencarbu

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [changelog](CHANGELOG.md).

## Documentation

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

[GNU AGPL v3](LICENSE). Map data © OpenStreetMap contributors (attribution will be shown in the app).
