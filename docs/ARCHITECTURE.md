# Architecture

🇫🇷 [Version française](ARCHITECTURE.fr.md)

## Principles

- **Readable first.** Every public class and function has a KDoc comment; non-obvious
  choices are explained with a *why* comment.
- **Light and fast.** All ~10 000 stations are kept in memory (a few MB). Colours are
  computed in ~0.3–2.5 ms (measured on a desktop JVM; to be re-measured on a phone).
  The map will be drawn by the GPU through a MapLibre GeoJSON layer, never as thousands
  of Android views.
- **Testable core.** Business logic is plain Kotlin with no Android import, so its
  tests run on a laptop in seconds.

## Layers (package `fr.opencarbu`)

| Package | Content | Android-free? |
|---|---|---|
| `domain.model` | `Station`, `FuelPrice`, `FuelType`, `BoundingBox` | yes |
| `domain.pricing` | `NationalPriceScale` (national quartiles → 4 colours), `PriceRange` (fine ratio), `PriceColors`, `ViewportPricing` (the feature's core) | yes |
| `data` | `StationMapper` (feed record → `Station`), `StationRepository` (cache + refresh logic), `FeedConfig`, `OkHttpFeedDownloader`, `MoshiFeedParser` | all but the last two: yes |
| `ui` | `MainViewModel`, `UiState`, `MapCamera` | no |
| `ui.map` | `MapScreen` (Compose), `StationMapView` / `StationMapController` / `StationLayers` (MapLibre), `StationGeoJson` + `MapDataFactory` (pure: stations → GeoJSON) | the last two: yes |

## Data flow

1. **Startup**: `MainViewModel` reads the cached file (instant, works offline) and shows it.
2. If there is no cache, or it is older than the user's refresh interval (setting, default 8 hours, from 1 minute
   to 48 hours; `DataSettings.isStale`), the feed is downloaded in the background. The same check runs
   each time the app comes back to the screen (`MainActivity.onStart` → `MainViewModel.onAppOpened`),
   because a process kept alive in the background does not restart the ViewModel.
   The request asks only for the columns `StationMapper` reads (`select=` parameter, built from
   `StationMapper.requestedColumns`), which keeps the download small. The response is streamed to
   a temporary file.
3. The file is parsed as a stream (one record in memory at a time) into `Station` objects.
   Only if it is valid does it replace the cache: a failed or corrupt download never destroys good data.
4. All stations stay in memory; the map colours are computed from that list (see below).

**Why a file and not a database (Room)?** The app always needs *all* stations to colour the map, so
queries would never help. A file avoids a heavy dependency and build step, and re-parsing it at
startup is fast. Revisit this only if a feature needs filtered queries (e.g. price history).

## The map

- **Engine**: MapLibre Native (`org.maplibre.gl:android-sdk`, BSD licence, no API key, GPU rendering),
  used directly through an `AndroidView`. The newer `maplibre-compose` wrapper was left out: it is
  pre-1.0, and the plain SDK API is stable and well documented.
- **Background map**: OpenFreeMap "Liberty" vector style (free, no key). One URL in
  `StationMapController.STYLE_URL`. The attribution button stays on (required by the data licences).
- **Dots**: a single GeoJSON source, three GPU layers (`StationLayers`): coloured dots, red shortage
  badge, price text. No Android view per station, so ~10 000 dots stay smooth.
- **Zoom behaviour**: small unlabelled dots when zoomed out; from zoom 8 prices are written on the dots
  (colliding labels are hidden, the cheapest wins) and shortage badges appear. All sizes are "zoom stops"
  in `StationLayers`, easy to tune.
- **Size factor**: `MapDisplaySettings.stationSizeFactor` (0.5 to 2.0, default 1.0, saved as `station_size_factor`) multiplies the radius of the dots and badges and the size of the prices. At 1.0 the look is exactly the stops written in `StationLayers`; the future settings screen will change the factor.
- **Flow**: the map reports its visible area when it stops moving → `MainViewModel` recomputes the dots
  on a background thread (`MapDataFactory`) → the new GeoJSON text is pushed to the map. An obsolete
  computation is cancelled when a newer one arrives.
- **Fonts**: price text uses "Noto Sans Bold", which the OpenFreeMap style serves. If the style is changed,
  check that this font exists there, otherwise labels disappear.
- **Place names**: a fifth layer reuses the base map's own `place` data (source `openmaptiles`) to write
  city/town/village names above the dots, at every zoom, in semi-transparent dark text with a white halo.
  The base map's own labels from that data (all but countries and regions) are removed at style load (`removeBaseMapPlaceNames`), so only these remain. Default opacity 0.8 (`MapDisplaySettings`, saved under `place_name_opacity`; a settings screen will expose
  it). `textIgnorePlacement` is on: names never hide a price. The base map draws its own names underneath too;
  this is accepted. Assumptions to re-check if the style changes: source id `openmaptiles`, source layer
  `place`, font "Noto Sans Regular".
- **User location**: `ui/location`. Uses Android's `LocationManager` directly (no Google Play Services, so
  F-Droid compatible) and the location permission (approximate is enough; precise is optional and lets GPS be used on phones with no network provider). Every available provider is tried, first answer wins, 15 s timeout. A last known position under 1 hour old is
  used as is, otherwise a fresh network fix is requested. At startup (once per launch, silently) and on the
  "my location" button, the map is fitted to `BoundingBox.around(position, radius)` (default 15 km, set by the user in Settings, setting `start_radius_km`). A blue marker shows the
  position. Failure messages are only shown for explicit button presses.

## The station sheet

Tapping a dot opens a bottom sheet (`ui/details/StationSheet`). The tap is resolved in
`StationMapController.handleTap`: every dot within 20 dp of the finger is a candidate, the nearest wins.
What the sheet shows is computed by the pure `StationDetailsBuilder` (unit-tested), using the same
price-age and shortage rules as the dots, so the sheet and the map never disagree.

- **Brand and name**: **the official feed has no station name, brand or logo**, so they come from a
  separate reference file (CSV built from OpenStreetMap, ODbL, published by Chiffrex on data.gouv.fr; URL in
  `FeedConfig.NAMES_URL`). `StationNamesRepository` keeps it in `cacheDir` and downloads it again only after
  a configurable delay (setting, 30 days by default); a failure keeps the old copy and is reported with the real error (toast, plus text in Settings and Info; the Info page also shows how many names are loaded). The published file is compressed with bzip2 (`.csv.bz2`, detected from its first bytes); Android cannot read that format, so `Bzip2InputStream` is a small decoder written for the app (no dependency), tested against files made by the reference compressor, with CRC checks. `StationNamesParser` reads three
  columns by header name: `ref:FR:prix-carburants` (the official id = the feed's `id`; **not** `osm_id`, which
  is the OSM object number), `brand` and `name`, plus `lon`/`lat` (the OSM position, which replaces the feed's coordinates for the dots, distance and map-app pin: `StationPositions`), `addr:housenumber`, `addr:street`, `addr:postcode`, `addr:city`: the sheet prefers this address to the feed's, which is wrong for some stations (it falls back to the feed's when the file has none). It handles quoted fields (own `CsvReader`) and repairs text
  encoded twice. `StationIdentity.lines` shows brand then name, without repetition. The sheet shows them above
  the address. Logos are not done: trademarks need a licence check, the safe default would be a coloured disc
  with the brand's initials.
- **Journey cost line**: under the distances, "A€ (trip) + B€ (full tank) = C€", followed by a small italic hint that the consumption and tank volume are set in the settings and, when routing is off, a second hint inviting to enable the road distance for a more accurate trip cost (`JourneyCost`,
  also behind the nearest list's amounts): the trip is the distance (by road once the routing server has answered, in a
  straight line otherwise or when routing is off) times the consumption setting, one way, at the station's price for the
  fuel shown on the map; the fill-up is the tank-volume setting times that price; the total adds the two amounts rounded
  to the cent. Hidden when the station does not sell that fuel or the user's position is unknown. The old warning about
  approximate addresses was removed: addresses now come from the OpenStreetMap-crossed reference file.
- **Bird in the distance line**: the straight-line distance is told "on the back of a <bird>" (`DistanceByBird`
  in the sheet). The bird is drawn when the sheet opens (`rememberSaveable` random number: new bird at each opening,
  same bird after a rotation) from `assets/birds.tsv`, read by `BirdListParser` at startup. `BirdPhraseBuilder` picks the
  name in the phone's language (French, else English; English falls back to the scientific name), the little word
  ("de"/"d’" with aspirated-h exceptions, "a"/"an") and the Wikipedia page (phone language first, else the other one,
  else no link). The translated sentence carries a marker where the bird goes. The full list (every bird with a
  French name) is generated from Wikidata (CC0) by `tools/birds/build_birds.py` (query in `birds.sparql`); the file
  in the repository starts as a small hand-written list.
  The same line also gives how long that bird would take ("· 5 min"). `BirdFlight` estimates each bird's speed from its
  body mass with the allometric law of Alerstam et al. 2007 (PLoS Biology 5(8): e197): speed = 15.9 × mass^0.13
  (m/s, kg), kept within the observed 8–23 m/s; a bird without a known mass flies at 40 km/h (the shipped list has a mass for 4,353 of its 11,334 birds). The masses are the
  optional sixth column (grams) of `birds.tsv`, fetched from Wikidata (property P2067) by a second query
  (`birds_mass.sparql`); `build_birds.py --mass-input` accepts an export of that query. It is an estimate of a
  typical cruising speed, not a measure for the species.
- **Distance**: for now the straight-line distance (haversine, `GeoPoint.straightLineKmTo`), labelled "as
  the crow flies". Road distance needs a routing engine. Open-source options: (a) public OSRM/Valhalla
  demo servers — free, no key, but their usage policies forbid heavy or production use; (b) OpenRouteService
  — open source, free tier with an API key (the key would have to ship in the app); (c) a Valhalla or OSRM
  instance hosted by the project — best quality and privacy, but costs money and maintenance; (d) an
  on-device engine (e.g. embedded Valhalla/GraphHopper with downloaded map regions) — fully offline, heavy
  (hundreds of MB, large dependency). Whatever is chosen, it should sit behind a small interface so the
  straight-line value remains the fallback.
- **Fuels**: every fuel sold or in recent shortage, with price, relative and absolute update date; old
  prices and shortages are shown in the error colour.
- **Map button**: an Android `geo:` link (`MapAppLauncher`), so the user's own map app opens (OsmAnd,
  Organic Maps, Google Maps...), from which a route can be started. No app-specific API is used.

## Road distance (optional)

Off by default (`RoutingSettings`, Settings > Road distance) because it sends the user's position and the
station's to a server. When on, a station sheet asks an OSRM-compatible server
(`data/RouteProvider`, `OsrmRouteProvider`; default: the FOSSGIS public instance
`https://routing.openstreetmap.de/routed-car`, editable) for `/route/v1/driving/lon,lat;lon,lat` and shows
"x km by road · y min" under the bird line. `MainViewModel.roadRoute` serialises requests, spaces them by
at least 1.1 s (the public server allows one per second), keeps the last 50 answers in memory (key: server,
user position rounded to ~100 m, station position) and the sheet waits 400 ms before asking. Pure parts
(`OsrmRequest`, `OsrmResponseParser`, URL validation) are unit-tested. Failures are shown on the sheet;
nothing else depends on this feature.
  `OsrmRouteProvider` shares one `OkHttpClient` (connection kept alive between requests, IPv4 addresses tried first), so only
  the first request pays for DNS and TLS; each request's duration is shown in Info › Performance (`TimedFeed.ROUTING`).

## Nearest stations

A button with a drawn right-pointing arrow (under the magnifier; the locate button moved one slot down) opens
`ui/nearest/NearestStationsPage`. It is greyed out and inert while `UiState.userLocation` is null. The list
comes from `NearestStations.candidates` (stations with a price for the selected fuel, reference-only ones
excluded, within `NearestSettings.radiusKm` — 30 km by default, 5–100 km in Settings; bounding-box pre-filter
then haversine; the 300 nearest at most) and `NearestStations.rank`. Each row: name, city and distance on the left; on the
right the price and three right-aligned lines "Fill-up cost: X€", "Trip cost: Y€" and "Total cost: Z€" (`NearestEntry.totalCostEuro`, the sum of the two amounts rounded to the cent as displayed). The page header recalls the configured fill-up volume and fuel consumption. The fill-up cost: the fill-up cost = `tankLitres × price` (tank size is a
setting, 45 L by default, 5–120 L) and the trip cost = `distance × consumption / 100 × price` (one way; consumption is a
setting, 6 L/100 km by default). The distance has the same source as the sheet's road distance: when road distance is on, OSRM
`table` requests give the road distances, nearest first in a straight line (`NearestStations.roadBatches`): groups of 50
stations per request (`ROAD_BATCH_SIZE`): up to 50 stations are answered by the single first request, a longer list takes
one more request per 50. The public server takes about 10 s per request whatever the number of destinations (measured, see
Info > Performance), so a few big requests fill the list much faster than many small ones. The page asks the groups one
after the other and `MainViewModel.roadDistances` still spaces requests by at least 1.1 s (as `roadRoute`). The rows update after each answer (the
list is re-sorted, a notice shows "n of m stations"), so the nearest ones can be read while the others arrive; a row not
answered yet shows its straight-line distance, labelled as such. If a later request fails, the rows already
answered keep their road distance and a notice says the rest is in a straight line (`OsrmTableRequest` / `OsrmTableParser`);
the list first shows straight-line distances. Road distances are cached per station id (`domain/nearest/RoadDistanceCache`, held by the ViewModel, so it survives closing the page): reopening within 100 m of the position they were computed from (and with the same server) shows them at once with no request, and only stations not yet known are asked for; moving further, or changing the server, forgets them. If road distance is off or the request fails, straight-line distances are kept and a
notice says so. Tapping a row closes the page and opens the station (`onNearestChosen`). Pure parts are unit-tested.

## Map behaviour around the station sheet, and loading

- **Sheet and camera**: when a sheet opens, `StationSheet` reports the y of its top edge (`onTopEdgeChanged`,
  minus the 48 dp drag handle). Once it has stopped moving (150 ms), `MapScreen` builds a `StationFocus` and
  `StationMapController.focusStation` moves the camera so the station sits in the middle of the map part above
  the sheet (camera centre shifted south by the right number of pixels, converted to degrees of latitude using a scale measured on the map itself, so it is right at any screen density; zoom kept,
  or brought to 10 when the station was chosen from the nearest list). Closing the sheet moves nothing: the map stays where it is.
  Kept light on purpose: the sheet's edge is read through `snapshotFlow` (never in composition, it changes every frame
  while the sheet slides); one move per station, 400 ms; and when the move only slides the map (no zoom change, station already on screen, area not already held back)
  the controller hands the app the previous area again, so the dots are not rebuilt while the sheet opens (the strip
  uncovered lies under the sheet). `refreshArea` brings the app up to date 450 ms after the sheet is gone, and any
  touch or interruption makes the real area be reported at once. The Map setting "Center the station..." turns the move off.
- **Loading arrow**: `ui/map/LoadingArrow` is shown at the centre of the screen (80 dp, twice the width of a map
  button) while no station is known yet and a download runs, or while stations are known but the map has not
  reported its first area; it gives up after 20 s (offline with no map style). It is a black ring-shaped arrow
  drawn with strokes on a rounded rectangle with the buttons' look, shown as 60 images (6° apart), the next one
  as soon as 16 ms have passed, synchronised with the screen refresh (`withFrameNanos`): about one turn per second
  on a 60 Hz screen. It does not intercept touches.

## Price history and daily refresh in the background (both off by default)

**History** (`HistorySettings`, Settings > Price history). `PriceHistoryRules.changesToRecord` (pure, tested) compares
the prices of a download with the last recorded ones and keeps only the CHANGES: a price is recorded when there is none
yet, or when the station's own update date is newer AND the price differs. Prices are whole thousandths of a euro.
`data/PriceHistoryStore` is a small SQLite file (built into Android, no library) in `noBackupFilesDir`, so it stays out of
the cloud backup, whose size limit it could exceed: table `changes` (station, fuel, moment, price; clustered on that key,
about 15 bytes a row, an estimated few hundred kB a day for the whole country) and table `latest` (last price per station
and fuel, read in one go to decide what changed). Retention is a setting (3 months, 6 months, 1 year, 2 years, for ever);
older rows are deleted, but each series keeps its last row, the price in force. Recording happens after every successful
download (`MainViewModel.recordHistory`; a storage error is silent, it never looks like a failed download) and when the
history is switched on (from the prices already held). The station sheet shows `PriceHistorySection`: a step curve drawn
on a Canvas, with a filled dot on each recorded price (not on the start and end points of the window); tapping a dot opens a small bubble (price and date, `surfaceContainerHighest` theme colour with a shadow; the system tooltip cannot be anchored to a point) and tapping elsewhere closes it, for the selected fuel (30 days, 3 months, 1 year, all), built by `PriceHistoryRules.curve`. Settings show the
space used and a button to delete the history (reset-all keeps the data, it only switches the settings off). With one
download a day, a price changed twice in a day is seen once.

**Database structure** (`price-history.db`, SQLite, schema version 1), two tables:

| Table `changes` (the history) | Type | Meaning |
|---|---|---|
| `station` | INTEGER | station id of the open-data feed |
| `fuel` | INTEGER | position of the fuel in the `FuelType` enum: 0 DIESEL, 1 SP95, 2 E10, 3 SP98, 4 E85, 5 LPG (never reorder that enum) |
| `at` | INTEGER | moment the station reported the price, epoch milliseconds (UTC) |
| `price` | INTEGER | thousandths of a euro per litre (1789 = 1,789 €) |

Primary key `(station, fuel, at)`, `WITHOUT ROWID` (the table is its own index); extra index `changes_by_time (at)`.
Table `latest` (`key` INTEGER primary key = station × 16 + fuel, `at`, `price`) is only a working copy of the last row of
each series, to detect changes quickly; it can be rebuilt from `changes` and is not exported.

**Export** (Settings > Price history > Export the history): CSV, UTF-8, header `station_id,fuel,changed_at,price_eur`, one
line per change sorted by station, fuel and date (`PriceHistoryCsv`): `1000001,DIESEL,2026-10-15T08:30:00Z,1.789`. Fuel by
name, ISO 8601 UTC dates and a decimal point whatever the phone's language. The user picks the destination with Android's
file picker (no storage permission); the file is written row by row, never fully in memory. It holds the whole history
(every station, fuel and recorded change); the file name carries the date.

**Import** (Settings > Price history > Import the history): reads a file of that format (any file can be picked; the first
line must be the header `station_id,fuel,changed_at,price_eur`, extra columns after the fourth are ignored, so a future
richer export stays readable) and ADDS its lines (`PriceHistoryStore.importCsv`, one transaction, line by line, never fully
in memory). Nothing existing is changed or erased: a line whose (station, fuel, moment) is already present is ignored.
Skipped too: unreadable lines (`PriceHistoryCsv.parseLine`: unknown fuel, bad date or number, price outside 0.100 to
10.000 €/L), dates in the future, and lines older than the retention setting. Afterwards `latest` is rebuilt from the newest
row of each series, so the next downloads compare with the imported prices. The result is reported by a message
(added / skipped). Use: back up the history, move it to a new phone, or give it to a friend. Works whether or not the
history is switched on.

**Background refresh** (`data/DailyRefreshJobService`, Settings > Background refresh). No service runs permanently: a
`JobScheduler` periodic job (24 h period, 6 h flexibility, so Android can batch it with other wake-ups), requiring an
unmetered network (Wi-Fi; a setting allows any network) and a battery that is not low, persisted across reboots
(`RECEIVE_BOOT_COMPLETED`, no user prompt). It skips the download when the cache is under 12 h old (the app was opened),
otherwise downloads the feed into the same cache file, and records the changes when the history is on. A job that fails or
is stopped by the system is retried later. Android may delay it (power saving, rarely used app); it does not run if the
app was force-stopped. Each run that goes to the end (downloaded, or skipped because the data was recent) stores its
date in the `settings` preferences (`DailyRefreshJobService.lastRefreshMillis`); Settings shows it under the section
title ("Prices last refreshed in the background on …"), in red when the option is on and the date is over a day old, to
tell at a glance whether Android really runs the job. It is read each time the app opens; nothing is shown before the
first run.

## Test option: stations missing from the price feed

`Settings > Data > Test: show stations missing from the price feed` (off by default) adds,
through `StationPositions.apply(..., includeReferenceOnly = true)`, every reference-file entry that has a
position but no station in the feed. They are `Station(isReferenceOnly = true)` without prices, drawn as
violet dots with a "?" (`PriceStatus.REFERENCE_ONLY`) whatever the selected fuel, outside the price scale.
The Info page counts them. Default: `DEFAULT_SHOW_REFERENCE_ONLY_STATIONS`.

## Address search

A magnifier button (drawn, same size as the menu button, right under it) opens a full-screen page
(`ui/search/AddressSearchPage`). Typing (3+ characters, 400 ms debounce; the keyboard's search key
searches at once and uses the first result, or the first result already shown) queries the IGN Géoplateforme geocoder (`data/AddressSearcher`, OkHttp, no API key,
only the typed text is sent). `domain/search/AddressResponseParser` turns the GeoJSON answer
(already read into Maps/Lists) into `AddressResult`s and is unit-tested. Choosing a result sets
`areaToShow` (a box of 10 km around the point, whatever the kind of place), which the map fits like after a location fix.

**Station identifier (debug aid, deliberately not advertised in the search field's hint).** A text of only 6 to 9 digits (`StationIdQuery`, a leading zero is accepted) is first looked up among the known stations (`MainViewModel.searchAddress`): on a match the only result is "Station 1120005 · name · postal code city" (`AddressResult.stationId`) and no request is sent; choosing it opens the station's sheet and the map goes to it like for a station picked in the nearest list (zoom 10 at least). No match, or a shorter number (a postal code): normal address search.

## The menu (Info and Settings)

A burger button (three drawn bars, top right under the fuel selector) opens a small menu with two pages
(`ui/menu`), shown full-screen over the map; the Back button closes them.

- **Info**: number of stations loaded, download date, and the sources and licences (data.economie.gouv.fr —
  Open Licence 2.0; OpenFreeMap / OpenMapTiles; OpenStreetMap — ODbL; MapLibre — BSD; OpenCarbu — AGPLv3).
  Tapping a source opens its website (the sources include the icon's duck: SVG Repo, CC0). Under the sources, a **Performance** section: for the prices feed and the names
  feed, how long the last successful download took (`FeedTiming`: server response time = request sent to headers
  received, and total time to the last byte written; measured by `OkHttpFeedDownloader.onTimed`, kept in the `settings`
  preferences by `FeedTimingPreference`, so it survives restarts; the background job records the prices feed too), and
  the memory used by the app in MB (the process PSS from `ActivityManager.getProcessMemoryInfo`, read every 2 s on a
  background thread, only while the page is shown).
- **Launcher icon**: an adaptive icon (`res/mipmap-anydpi-v26/ic_launcher*.xml`): a 512 px picture (`res/drawable-nodpi/ic_launcher_foreground.png`, pump and duck) over a plain sky-blue background (`ic_launcher_background`). The launcher masks it with its own shape and may shrink it, so only the central two thirds are always visible; the artwork sits inside that zone. minSdk 29 needs no older-style icon files.
- **Edge to edge**: `MainActivity` calls `enableEdgeToEdge`, so the map is drawn behind the system bars on every Android version (Android 15 forces it anyway). Both bars are transparent (dark icons); `MapScreen` draws the status bar's tint over the map: light grey mixed with 18 % of the theme's main colour (so it follows the Material You accent on Android 12+), at 45 % opacity (`STATUS_BAR_GREY`, `STATUS_BAR_TINT`, `STATUS_BAR_OPACITY`). Every overlay already pads itself with `statusBarsPadding` / `navigationBarsPadding`, overlays and pages use the modifiers of `ui/Insets.kt` (`insetsAllSides`, `insetsTopAndSides`, `insetsBottomAndSides`: system bars AND camera cut-out, wherever they are; in landscape the 3-button navigation bar and the cut-out are on the sides, and change with the way the phone is turned), and `StationMapController.setBarInsets` moves MapLibre's attribution button away from the navigation bar.
- **Theme**: `OpenCarbuTheme` uses Material You dynamic colours (system accent) on Android 12+ and the standard palette below; always the light scheme, because the base map is light. Map price colours are fixed on purpose.
- **Settings** (sections: default fuel, price freshness, nearest stations, map, data, price history, background refresh, road distance): old-price and old-shortage thresholds, size factor of dots and prices, size factor and opacity of place names, refresh interval,
  starting radius, the switch (in the Map section) that turns off the camera glide to a tapped station (`MapDisplaySettings.centerOnStation`, on by default; a station picked from the nearest list is still shown), default fuel ("last used" or a given fuel) and a manual refresh button. Every value is
  saved in the app's preferences (see `data/*Preference`) and applied immediately. Sliders apply their value
  when the finger is lifted, so the map is not recomputed during the drag.
- The pages only talk to the app through `MenuActions` (a handful of callbacks), and read `UiState`.

## How a dot gets its colour

1. The map reports its visible area (`BoundingBox`) when the camera stops moving.
2. `ViewportPricing.compute` keeps stations that are in view and concern the chosen fuel,
   and gives each one a **status** (`PriceStatus`):
   - `FRESH`: price updated within the last 90 days → coloured on the green→red scale;
   - `STALE`: older price (or unknown date) → **grey** dot, price still shown. The default is generous
     on purpose: many stations keep a stable price for weeks (E85 especially);
   - `TEMPORARILY_OUT`: the station reports a temporary shortage of this fuel that began
     less than 30 days ago → **grey** dot with a **red badge**. Older "temporary" shortages are
     ignored: a station with no price either is not drawn at all.
3. `NationalPriceScale` is built from the FRESH prices of ALL the stations of the country for that fuel
   (`ViewportPricing.nationalScale`), not only the visible ones, so a dot keeps its colour when the user
   pans or zooms. Grey stations are left out so they cannot distort it. `MainViewModel` builds it once and
   reuses it while panning (rebuilt when the stations, fuel or thresholds change, and hourly).
4. The colour is one of four `PriceBand`s: **red** above the third quartile, **orange** above the median,
   **yellow** above the first quartile, **green** otherwise (a price equal to a quartile belongs to the lower
   band; if all prices are equal everything is green). `PriceRange` (5th–95th percentile) still gives a fine
   0–1 `ratio`, used only to decide which label wins when two collide.

Both thresholds (90 days for prices, 30 for shortages) live in `PricingSettings`, are saved with
`SharedPreferences` (`PricingSettingsPreference`) and flow from `MainViewModel` down to `ViewportPricing`.
`MainViewModel.setPricingSettings` is ready for the future settings screen; changing it redraws the map.

The selected fuel is saved the same way (`FuelPreference`) and restored at launch.

## Feed format (verified 2026-10-08)

`StationMapper` was checked against two real records of the v2 dataset. What matters:

- One record per station (≈ 9 800 in total), each fuel in its own columns:
  `gazole_prix` / `gazole_maj`, `sp95_…`, `e85_…`, `gplc_…`, `e10_…`, `sp98_…`.
  A fuel not sold or out of stock has `null` in both columns.
- Position: `geom` = `{lat, lon}` in degrees. The `latitude` / `longitude` columns are
  **strings in 1e-5 degrees** (`"4377300"` = 43.773°); the mapper handles both.
- `horaires_automate_24_24` is `"Oui"` / `"Non"`.
- Update dates are ISO-8601 (`2026-10-07T08:54:39+00:00`). Some prices are old
  (e.g. 8 to 10 days in the samples), so the update date will be shown to users.
- **Shortages.** `<fuel>_rupture_type` is `"temporaire"` or `"definitive"`, and
  `carburants_rupture_temporaire` lists the temporary ones as text (`"Gazole;E85;E10;SP98"`).
  A fuel in shortage has **no price** (null), so a station can have shortages and no price at all.
  About 2 200 of the ~9 800 stations report at least one temporary shortage, and some have done so
  for over a year, so "temporary" is not always short-lived. The start date is in `<fuel>_rupture_debut`
  and is what the 30-day rule relies on.
- Other columns exist (opening hours, services…) and are ignored for now.

To re-check against a fresh download:

```bash
curl -sSL "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records?limit=2" -o sample.json
```

## Roadmap

1. ~~Download the full feed and cache it locally.~~ Done (to be validated on a device).
2. ~~Map screen (MapLibre, OpenFreeMap tiles) with coloured dots + fuel selector.~~ Done (to be validated on a device).
3. Station details (all prices, last update, 24/7 flag), user location, search.
