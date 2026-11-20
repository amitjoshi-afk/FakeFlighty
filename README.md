# Contrail

A personal flight tracker for Android. Contrail pulls trips from your calendar, looks up live status, and alerts you when a gate, terminal, delay or status changes.

## Features

- **Trips** grouped from your flights, with upcoming and past sections
- **Live status** from AeroDataBox: scheduled vs. revised times, delay, terminal, gate, check-in desk, baggage belt, aircraft
- **Flight timeline** from boarding (estimated) to gate departure, takeoff, landing, arrival and baggage
- **Progress bar** while you are in the air, with time remaining
- **Alerts** for gate, terminal, delay (10+ min shifts), boarding, departure, arrival, cancellation and diversion
- **Stats** by year or all time: flights, distance, time in the air, airports, airlines, on-time share (arrived 15 minutes late or less), average delay, longest flight, top route

## Getting trips in

Contrail never asks for your Microsoft password. There are three ways to add flights:

1. **Phone calendar.** In Outlook for Android, go to Settings, tap your account, and turn on *Sync calendars*. Contrail then reads those events through Android's calendar provider (`READ_CALENDAR`). Some company device policies turn this switch off.
2. **.ics files.** Export a calendar from Outlook, or open the `.ics` attachment in an airline confirmation email, and share it to Contrail (or use *Open .ics file*).
3. **By hand.** Enter a flight number and date.

The parser looks for an airline flight number (`SQ638`, `SQ 638`, `3K521`) and, if it can, a route (`SIN-NRT`, `Singapore (SIN) to Tokyo (NRT)`). Matches with low confidence start unticked on the review screen.

## Live data: AeroDataBox

Contrail calls `GET /flights/number/{flightNumber}/{dateLocal}`. AeroDataBox is sold through two marketplaces, and both are supported in Settings:

| Provider | Base URL | Key header |
| --- | --- | --- |
| RapidAPI | `https://aerodatabox.p.rapidapi.com` | `X-RapidAPI-Key` (+ `X-RapidAPI-Host`) |
| API.market | `https://prod.api.market/api/v1/aedbx/aerodatabox` | `x-api-market-key` |

The key is stored only in the app's private preferences on your phone. It is never committed to this repo.

Background refresh runs about every 30 minutes for flights in the next two days, so one watched flight costs roughly 48 lookups a day. Check your plan's quota.

## Project layout

- `core/`: plain Kotlin, no Android. Flight model, calendar and `.ics` parsing, AeroDataBox client and mapping, timeline, change detection, stats. Unit tested.
- `app/`: Jetpack Compose UI, phone calendar reader, OkHttp transport, WorkManager refresh and notifications.

## Building

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:test          # unit tests
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/
```

Every push runs the same steps on GitHub Actions. The debug APK is attached to each run as the `contrail-debug-apk` artifact, so you can download and sideload it without a local setup.
