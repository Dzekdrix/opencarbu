# Changelog

Newest first. Version numbers follow `versionName`; each F-Droid changelog is in
`fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.

## 0.21.4 (versionCode 55)
- Nearest stations: road distances are requested in groups of 50 stations (one request for up to 50 stations, one more
  per further 50), nearest first. The public routing server takes about 10 s per request whatever its size.

## 0.21.3 (54)
- Test build: one station per routing request.

## 0.21.2 (53)
- Routing client shared between requests (connection kept alive, IPv4 addresses first).
- Info > Performance: duration of the last request to the routing server.

## 0.21.1 (52)
- Icon source credited in Info; nearest stations page behaviour for road distances.

## Before 0.21.1
Price history (opt-in, CSV export/import), background refresh, performance section, edge-to-edge display, renaming to
OpenCarbu, nearest stations with fill-up and trip cost, optional road distance, address search, station brand and name.
