# Contrail

A personal flight history and stats app for Android. Contrail finds past flights in your calendar, maps them to airports offline, and shows where you've been: trips by year, distance, time in the air, countries, top routes and more.

Contrail has no internet permission. Everything stays on your phone.

## Features

- **History**: every flight, newest first, grouped by year and into trips ("Trip to Tokyo")
- **Flight detail**: airports with city and country, local departure and arrival times, duration, great-circle distance, domestic or international
- **Stats** for all time or one year: flights, international flights, time in the air, distance (times around the Earth, share of the way to the Moon), airports, countries, airlines, flights per year, top routes, top airports, top airlines, longest and shortest flight, first flight on record
- **Edit and add by hand** when a calendar entry is missing airports or times

## Getting flights in

1. **Phone calendar.** In Outlook for Android, go to Settings, tap your account, and turn on *Sync calendars*. Contrail reads those events through Android's calendar provider (`READ_CALENDAR`), going back 15 years. Some company device policies turn this switch off.
2. **.ics files.** Export a calendar from Outlook, or open an `.ics` attachment from an airline email, and share it to Contrail (or use *Open .ics file*). Outlook's Windows time zone names such as "Singapore Standard Time" are mapped to IANA zones for common cases.
3. **By hand.** Flight number, date, airports and optional times.

The parser looks for an airline flight number (`SQ638`, `SQ 638`, `3K521`) and a route (`SIN-NRT`, `SIN to NRT`, `Singapore (SIN) to Tokyo (NRT)`). Airport codes are checked against the bundled airport list, which filters out most false matches. Low-confidence matches start unticked on the review screen. Times come from the calendar entry's start and end.

## Data

- `app/src/main/assets/airports.csv`: 5,404 airports with IATA codes (large and medium airports, plus small ones with scheduled service), with ICAO code, name, city, country and coordinates. Built from [OurAirports](https://ourairports.com/data/), which is in the public domain.
- Airline names for about 50 common IATA codes are in `core/.../airports/Airlines.kt`. Other airlines show their code.
- Distances are great-circle distances, so they are a little shorter than the distance actually flown.

## Project layout

- `core/`: plain Kotlin, no Android. Flight model, calendar and `.ics` parsing, airport database, trips and stats. Unit tested against the bundled airport file.
- `app/`: Jetpack Compose UI and the phone calendar reader.

## Building

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:test          # unit tests
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/
```

Every push runs the same steps on GitHub Actions and attaches the debug APK to the run as the `contrail-debug-apk` artifact.
