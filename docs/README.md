# rootiest-status-android documentation

Native Android app for the Rootiest services status page.

## Policy: docs are the single source of truth

Everything about this project is documented here. A change is not done until the docs
under this directory describe it. Generated documents are never edited by hand: edit
the source and regenerate.

## What it does

The app shows the same snapshot as [status.rootiest.dev](https://status.rootiest.dev):

- a headline card in the overall level's colour, centred and fixed above the list (green ok, yellow warn, red bad), with
  "Failover active" while traffic is served by the standby edge;
- the Servers section (state, vitals, note per host), only when the API includes it:
  after signing in, for staff and the groups the catalog lets see servers;
- every group of services: state dot, uptime text and the per-day uptime strip, and
  for a service that publishes a backup report the hub's backup line ("Last backup 3 h
  ago (...)"), grey when unknown and yellow when the backup is older than the hub
  allows (`Backup`, rootiest-hub's `BackupView`; also on the detail screen). Its time
  follows Settings > Times, built from the hub's `At` and `Detail`; a hub that sends
  only `Text` is shown as it is.

Section titles (Servers, then each group) stick below the headline while their rows
scroll, until the next section's title pushes them off. The headline is fixed above
the list rather than a sticky header itself, because every sticky header in a Compose
list sticks to the same top edge and the next one would push it away too.

Tap a service to open its detail screen. The bell at the top mutes or unmutes its
notifications (crossed out while muted). Below it:

- a summary card, fixed above the list: state (and "planned" when a planned window expects it down), uptime
  text, the service's detail line, its hostname (a link to the site, except for services
  probed by TCP connect, whose hostname is only a name) and the uptime strip;
- **Recent changes**: the snapshot's events for that service (`Kind` `service`, `Subject`
  equal to its name), newest first, with times as the Times setting shows them;
- **Daily uptime (UTC days)**: one row per day, newest first, from the strip's cells.
  "Recent changes" sticks below the summary card until this title pushes it off. Days are
  UTC days, as rootiest-hub counts them, so tomorrow's date can appear in the evening. Days before
  the history began (`Class` `none`) are left out.

Pull down to refresh on either screen. After a failed refresh the last good snapshot
stays on screen with an error banner above it. Back (button or gesture) returns to the
list.

## Signing in

**Sign in** (in the account menu, top right) signs in with RootiestID, the Rootiest
single sign-on, through rootiest-hub, which then shows what that account may see, as on
the web page (staff see everything, including the Servers section). Without an account
the app shows the public view. The flow is rootiest-hub's app sign-in:

1. The app makes a random proof (32 bytes, base64url), saves it, and opens
   `https://status.rootiest.dev/app/login?challenge=<base64url SHA-256 of the proof>`
   in a Custom Tab.
2. After RootiestID, the hub redirects to `dev.rootiest.status:/signed-in?token=...`,
   which `MainActivity` receives (`singleTop`; the intent filter matches the scheme, and
   the activity checks the path).
3. The app keeps the token and proof in its private storage (`AuthStore`, preferences
   file `auth`) and sends `Authorization: Bearer <token>` and `X-Rootiest-Proof: <proof>`
   with every request: the app, the widget and the background job. The hub counts the
   token only with that proof, so a token caught on its way back is useless.

The top bar shows the RootiestID display name; tapping it opens the account menu, with
**Settings**, **Support** and **Sign out**, which deletes the token (and the support desk's, below).
The app reads the name and expiry from the token (signed by the hub, only read here). A
sign-in lasts 14 days, the hub's `STATUS_SESSION_TTL`; once it expires, or after the
hub's `signout-all`, the app shows the public view again and the menu offers **Sign in**
(after `signout-all` the name stays until the token's own expiry, but the view is
public). Notifications then include servers too; a server first seen after signing in
is not reported as a change.

## Account menu and settings

The top bar's right side opens the account menu: under the display name when signed in,
under an account icon when signed out. Either way it has **Settings**, **Support** (see
[Support tickets](#support-tickets)), then **Sign out** or **Sign in**, so settings are reachable without an account.

**Settings** is its own screen (back returns to the list), in sections: Notifications,
Instant alerts and Background check (see [Notifications and the background job](#notifications-and-the-background-job)), then Appearance,
Service list, Times and About:

- **Appearance**: **System default** (the default) follows the phone's dark theme;
  **Light** and **Dark** always use that one. Dark is Catppuccin Mocha, light is
  Catppuccin Latte, the status page's light and dark palettes. The choice is saved in
  the preferences file `settings` (`SettingsStore`). On Android 12 and later the app
  also gives it to the system (`UiModeManager.setApplicationNightMode`), so the window
  shown before the app draws matches it too; on Android 10 and 11 that brief window
  follows the phone. The status and navigation bars' icons follow the choice. The
  home-screen widget always stays dark.
- **Service list**: **As on the status page** (the default); **Problems first**, which
  keeps every section in place and moves its down services (or unhealthy servers) to
  the top, in their order; or **Problems only**, which hides what is up or healthy and
  the sections left empty, and says "Nothing is down" when that leaves nothing. A
  standby copy is not a problem, as for the widget. Saved as `serviceList` in
  `settings`; `arranged` in `data/Problems.kt` does it, and only the list uses it (the
  widget, detail screens and notifications see every service).
- **Times**: **Relative** ("3 h ago", "2 days ago"; the clock once it is a week old) or
  **Clock time** (the default), and for the clock:
  - **Format**: **Default** (the locale's date, "Nov 2, 2026, 4:05 PM"), **ISO**
    ("2026-11-02 16:05") or **Custom**, a strftime pattern typed in Settings (default
    `%Y-%m-%d %H:%M`) with a live example. It knows `%Y %y %m %d %e %b %B %a %A %H %I %l
    %M %S %p %Z %z %%` (Settings lists what each gives); anything else, an unknown field
    too, is kept as written. A custom pattern is used as written everywhere, the widget
    included: it sets its own hours and adds no zone name unless it has `%Z`.
  - **Hours**: **Phone setting** (the default, the phone's "Use 24-hour format",
    read each time the setting is loaded), **12-hour** or **24-hour**.
  - **Time zone**: **Local time** (the default, the phone's zone) or **UTC** (with
    " UTC" after Default and ISO times).

  It covers the detail screen's recent changes and Settings' last check and last push.
  The widget's "Updated" time follows format, hours and zone but is always a clock time
  (the time of day, or the whole custom pattern), as a relative one would go stale
  between redraws; Settings says so while Relative is chosen. A change redraws the
  widgets a second after the last one (so typing a pattern does not redraw on every
  key). The examples under the choices are the current time less three hours. Daily
  uptime stays in UTC days, as the hub counts them. `TimeFormat` and `strftime` in
  `data/Times.kt` do the formatting; saved as `time-style`, `time-date`,
  `time-pattern`, `time-hours` and `time-utc` in `settings`, and provided to every
  screen as `LocalTimeFormat`, the backup line's time included.
- **About**: the version (name and code), then links that open in the browser (the
  status page, the source code), and **Report a problem**, which opens the app's new-ticket form
  (see [Support tickets](#support-tickets)) filled in by `reportProblemTicket` in `data/Support.kt`:
  category Problem, about the Android app (a support topic on the desk, not a service the status page checks), and the app version, Android version and phone, for the
  person to review and send. Last,
  **Open-source licenses** (`ui/LicensesScreen.kt`) lists the libraries the app is
  built with and their licenses, tap one for its text. The AboutLibraries Gradle plugin
  writes that list at build time from the release dependencies, as
  `res/raw/aboutlibraries.json`, so it is never out of date.

## Support tickets

**Support** in the account menu, or the **Support** shortcut on a long-press of the app's
launcher icon, lists your tickets on the support desk
([support.rootiest.dev](https://support.rootiest.dev), rootiest-hub's desk) and opens each one's
thread, where you can reply and close it. **New ticket** (or **Open a ticket** when there are none)
opens a ticket, as on the desk's web form.

- **Its own sign-in.** The desk is a separate RootiestID client with its own key, so the status
  page's token does not work there. The first time Support opens without the desk's sign-in, the app
  starts it on its own (once per run of the app, so cancelling it does not loop; the screen then has
  **Sign in to the support desk**). It is the same flow as the status page's: a new proof, the
  desk's `/app/login?challenge=...`, and back to `dev.rootiest.status:/support-signed-in?token=...`,
  which `MainActivity` hands to `SupportViewModel`. If RootiestID remembers you it is a brief browser
  visit with no prompt (and a one-time consent screen). The token and proof are kept in the
  preferences file `support-auth` (`AuthStore.forSupport`); a sign-in lasts 30 days.
- **No RootiestID yet.** Under the sign-in button, **Create a free RootiestID** opens
  `https://auth.rootiest.dev/signup` in the browser, as on the desk's landing page; you come back
  and sign in when done.
- **Signing out** of the app clears both sign-ins. When the desk refuses the token (expired, or its
  `signout-all`), the app drops it and asks for a new sign-in.
- **What it shows.** Your own tickets only, newest change first, with whose turn it is (**Waiting for
  support**, **Answered**, **Closed**), the service (or the category) and the last change, in the
  Times format. A thread shows the subject, status, category, service, importance and when it was
  opened, then the messages oldest first: staff replies are from **Rootiest Support**, in a lighter
  card. Staff-only notes never reach the app (the desk does not send them). Messages are Markdown,
  drawn as styled text (`ui/Markdown.kt`, with commonmark-java): bold, italics, code, headings,
  lists, quotes and links. Raw HTML is dropped and an image is its description, as on the desk, and
  only http(s) and mailto links open (in a Custom Tab).
- **Replying and closing.** The thread has a reply box at the bottom (Markdown, up to 5000
  characters) and **Close ticket** in the top bar while the ticket is open; a closed ticket's box
  says a reply reopens it, as on the desk. The text stays in the box until the desk has taken the
  reply, so a refused or failed send loses nothing, and the desk's reason (its hourly limit, say)
  shows above it.
- **Opening a ticket** (`ui/NewTicketScreen.kt`): category, service ("Not about a specific
  service", or one of the services the desk offers this person, from its `/api/form`), importance,
  subject (up to 150 characters), message (up to 5000) and "Email me when support replies". The
  desk checks it again; its refusals show above the form, in its words. Once it is taken, the new
  ticket's thread replaces the form. A prefilled service the person may not see is left out, as on
  the web form.
- **Reply notifications.** After signing in to the desk the app gives it the phone's push address
  (`POST /api/push`, the same UnifiedPush address the status page gets), and the background check
  renews it on every run, since the desk forgets one not renewed for 30 days. When staff reply to
  one of your tickets, or close it, the desk posts `ticket <id>` to that address and nothing more.
  `StatusPushService` tells it from the status page's `refresh` (`ticketPush`) and queues
  `SupportReplyWorker`, which fetches that ticket with the desk's sign-in and posts "Support replied:
  <subject>" with the reply's first line, or "Ticket closed: <subject>" (`ticketNotice`). Nothing
  is shown if the thread's last word is yours and it is still open. They go to the **Support
  replies** channel, which has its own row in Settings > Notifications. One notification per
  ticket; tapping it opens the thread over Support's list (`openTicket`). Signing out removes the
  address from the desk first (`DELETE /api/push`), so the phone stops hearing about the
  tickets. Test it with `curl -d 'ticket <id>' '<address>'` for a ticket of yours.
- **The API** is the desk's `/api/tickets`, `/api/tickets/{id}`, `.../reply`, `.../status`, `/api/push` and
  `/api/form` (rootiest-hub `docs/README.md`, "Support desk"), with the token and proof as for the
  status page. Every field is sent, defaults included (`encodeDefaults`), since Go reads a missing
  one as empty or false. A 401 means sign in again; other errors show the desk's own words.
- **The shortcut** is a static launcher shortcut (`res/xml/shortcuts.xml`, icon
  `mipmap-anydpi/ic_shortcut_support.xml`). It starts `MainActivity` with the action
  `dev.rootiest.status.action.SUPPORT`, and `openSupport` puts the ticket list over the status
  list, so back goes there (or back to the list if a ticket is open). Signed out of the desk, the
  usual sign-in starts.

## Home-screen widget

The **Rootiest Status** widget shows a coloured dot and a headline. At two cells tall or
more it also lists rows under it, each with its own dot (up to four, then "and N more"),
and when the snapshot was fetched. Tapping it opens the app.

Each widget chooses what it shows (`widget/WidgetConfigActivity.kt`, saved per widget id
in the preferences file `widgets` and forgotten when the widget is removed; the content
is `widgetContent` in `data/WidgetContent.kt`):

- **Everything** (the default): the status page's level and headline, then what is wrong.
- **One section**: the servers (when the viewer may see them) or one group of services,
  as "Smart home: all up" or "Code: 2 problems", then every row with problems first.
  The dot is red when one is down, yellow when the only problems are milder. A group
  renamed or removed since says "<name> is not in the status"; the servers when signed
  out say "Sign in to see the servers".

From Android 12 a new widget starts with Everything, and the choice is changed from the
widget's settings on the home screen (long-press it); on Android 10 and 11 the choice is
asked when the widget is added.

- What is wrong (`data/Problems.kt`): servers whose state is not `healthy`, then
  services whose state is not `up` or `standby` (a standby copy is down on purpose).
  "(planned)" marks one a planned window expects.
- Each widget update fetches `/api/status` and saves the answer in the app's files
  (`last-status.json`, `data/StatusCache.kt`). When the fetch fails the widget shows
  the saved snapshot, with "Offline · as of <time>" in yellow (the time in the zone the
  Times setting chooses).
- The background job (below) redraws every widget each time it runs (every 15 minutes by default). The widget's own
  `updatePeriodMillis` is 0, since the system's minimum is 30 minutes.
- The app's own refresh saves its answer there too and redraws every widget and the
  Quick Settings tile.

## Quick Settings tile

The **Rootiest Status** tile (`StatusTileService.kt`) is added from the edit screen of
the notification shade. It shows what an Everything widget shows: the overall headline
under the name ("All systems operational", "2 services down"), with an icon per level
(a tick when all is up, a warning triangle for milder problems, an error mark when
something is down).

- **Active** (lit) only when the level is `ok`; inactive when anything is wrong.
  Unavailable until the app has a saved snapshot (its first good fetch).
- It reads the saved snapshot (`last-status.json`) each time the shade opens, so it
  never waits on the network. The background job and the app's own refresh also ask it
  to redraw (`requestListeningState`) after saving a new one, so a push-triggered check
  updates it while the shade is open.
- Tap: opens the app. Long-press: opens the app's Settings (the activity answers
  `QS_TILE_PREFERENCES`).
- The content is `tileContent` in `data/TileContent.kt`, built from `widgetContent`, so
  the tile and the widget always agree.

## Wear OS

The watch app (module `:wear`) has a **Rootiest Status** tile, a watch-face complication and a list screen. It
shows what the phone app last fetched, so it needs the phone app, but no network or
sign-in of its own: the Servers section appears when the phone is signed in.

- **How the data gets there**: after each good fetch (the background job, a push, or a
  refresh in the app) the phone puts the snapshot on the Wearable Data Layer
  (`Watch.kt`, path `/status`, the JSON under `view`). The watch's `SnapshotListener`
  saves it in its own `last-status.json` and asks the tile to redraw. A newly installed
  watch app reads the item the phone left there. The Data Layer runs over Google Play
  services, and only connects apps with the same package name and signing key, which is
  why both apps are `dev.rootiest.status` signed with the release key.
- **The tile** (`StatusTile.kt`, ProtoLayout Material 3): the headline in its level's
  colour and the first three problems, then "and N more" (`watchSummary` in
  `data/Watch.kt`, built from the Everything widget's content). Tapping it opens the list.
- **The complication** (`StatusComplication.kt`), for a watch face's slots: a short
  text slot shows the level's icon and "OK" or how many are down ("3 down", servers
  included); a gauge (ranged value) slot shows the services up out of all of them; an
  icon slot shows the level's icon alone. It does not poll: each new snapshot asks the
  watch face for an update, so it changes as soon as the phone's snapshot arrives. A tap
  opens the list; the face's editor shows a sample until the first snapshot. The
  mapping is `watchComplication` in `data/Watch.kt`.
- **The list** (`StatusActivity.kt`, Wear Compose): the headline, then the servers and
  each group with problems first, as the phone's Problems first setting arranges them.
- **Installing**: the watch APK is `wear-rootiest-status-<version>.apk` on each release.
  Sideload it with adb over Wi-Fi: on the watch turn on Developer options (tap Build
  number seven times), then Wireless debugging; pair with
  `adb pair <ip>:<pairing port>` and the code it shows, then `adb connect <ip>:<port>`
  and `adb -s <ip>:<port> install wear-rootiest-status-<version>.apk`. Updates must be
  signed with the same key, as on the phone.
- **The emulator**: install a Wear OS system image and the emulator, create a Wear OS
  device in Android Studio's Device Manager (or `avdmanager`), and pair it with a
  phone with Android Studio's Wear OS pairing assistant. Without a paired phone the
  emulator's tile says to open the phone app.

## Notifications and the background job

`RefreshWorker` (WorkManager) runs every 15 minutes by default, the shortest period
WorkManager allows, or at the interval chosen in Settings (below), and only with a network connection, and at once when a push arrives (below). Each run fetches the status, posts a
notification for each change, saves the states and the snapshot, and redraws the
widgets. It is scheduled on every app start (and when a widget is added) and keeps
running without a widget, since it also sends the notifications. Android may run it
later than its interval when the phone is idle (Doze), so a change can take longer
to arrive; ntfy remains the fast path.

- A change is a service or server whose state differs from the previous run
  (`data/Changes.kt`; states saved in `last-states.json`). Nothing is posted on the
  first run, for a name not seen before, or for one a planned window expects (as
  rootiest-hub holds its own alerts for those). Nothing is posted for a muted name
  either (below).
- **Notify about an outage** (Settings, under Notifications): **At once**, **After 2
  minutes**, **After 5 minutes** (the default) or **After 15 minutes**. The hub changes
  a state on the page within about 30 seconds but alerts only once it has lasted 4
  minutes (`ALERT_AFTER`), so without a wait the app would notify blips the hub stays
  quiet about. An outage waits (`damp` in `data/Damping.kt`, kept in
  `pending-outages.json`) from when the hub saw it begin (its latest event for that
  name, or this run when there is none), so one first seen by a run long after it began
  is posted at once. One that recovers sooner is never posted, nor is its recovery; a
  recovery after a posted outage is posted at once, and an outage that gets worse while
  waiting keeps waiting from its start. While one waits, a one-off run is scheduled for
  when it is due (5 seconds after, or a minute after a failed fetch), so it arrives on
  time whatever the interval, push only included.
- Notifications go to one of three channels (`Notifier.kt`), so the phone's own settings
  can treat them apart: "Outages" (high importance: sound and a heads-up banner), for
  "Gitea is down / was up" and any other move away from green, "Recoveries" (low:
  silent, in the shade only), for "Gitea is back up / was down", and "Support replies"
  (default importance; see [Support tickets](#support-tickets)). Each name has one
  notification id across the first two, so a recovery replaces the down notification. Tapping
  one opens the app. The channels are created on every app start, so they show in the
  phone's settings before the first change; the single "Service changes" channel of
  1.3.0 and earlier is deleted then.
- On Android 13 and later the app asks for notification permission on first launch.
  Refusing only turns notifications off.

**Settings.** The settings screen (`ui/SettingsScreen.kt`) reads the phone's state each
time it shows, so it is current after a trip to the phone's settings:

- Notifications: when they are off (permission refused, or the app switched off), one
  row opens the app's notification page in the phone's settings, where Android 13+ also
  grants the permission. Otherwise a row per channel, saying "Off" when that channel is
  off, opens that channel's page; "Services to notify about" (below); and "Send a test
  notification" posts a sample outage on the Outages channel.
- **Services to notify about** (`ui/WatchListScreen.kt`) lists every server (when the
  viewer may see them) and every service by group, each with a switch. Turning one off
  mutes it: the background job drops its changes (`unmuted` in `data/Changes.kt`). The
  app saves the muted names, not the watched ones, in the preferences file `settings`
  (`SettingsStore.muted`), so a service added later is notified about until muted. The
  list comes from the snapshot on screen; the Settings row says "All" or "All but N
  muted". The same setting has two shortcuts: the bell at the top of a service's detail
  screen, and the **Mute** button on each change notification (`MuteReceiver.kt`, which
  mutes that name and clears the notification). `SettingsStore.watchMuted` keeps an open
  app in step with a mute from a notification.
- Instant alerts: whether push is on (and through which distributor app, with when the
  last push arrived), installed but without an address, or off with no distributor.
- Background check: how often the job runs on its own, every 15 minutes (the default),
  30 minutes or hour, or **Only when a push arrives**, which cancels the periodic job
  (`SettingsStore.refreshInterval`, preferences file `settings`). `RefreshWorker.schedule`
  applies it on every app start and on each change, with WorkManager's `UPDATE` policy,
  which changes a scheduled job's period without restarting its clock. Push only is
  marked in yellow while push is not on, since nothing would then run the job: no
  notifications and a stale widget. With push on, the widget then updates only after an
  alert. Below the choice, when the job last ran (the time `last-states.json` was
  written, which only the job does), and the phone's battery setting for the app
  (`Battery.kt`), the only part of "App battery usage" the system tells apps:
  **Restricted** (`isBackgroundRestricted`; in yellow, as no check or alert runs then)
  and **Unrestricted** (`isIgnoringBatteryOptimizations`) open the app's info page, where
  App battery usage changes it; **Optimized**, the default, opens the system's dialog to
  let the app run unrestricted (permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; lint's
  `BatteryLife` warning, a Play Store policy check, is suppressed, as the app is not on
  Play).

**Push.** The app uses UnifiedPush with the phone's distributor, the ntfy app. On start
it asks for the current or default distributor and registers; the distributor hands it a
push address, an `up...` topic on the ntfy app's default server, which must be
`https://ntfy.rootiest.dev` (rootiest-hub refuses addresses anywhere else). The app saves
it and sends it to the hub (`POST /app/push`) on every run of the job, so the host
serving the page learns it soon after a failover. After each alert the hub posts
`refresh` to every address; the ntfy app hands it to `StatusPushService`, which queues
the job at once (expedited on Android 12+). The support desk posts `ticket <id>` to the same
address instead (see [Support tickets](#support-tickets)), which goes to `SupportReplyWorker`. The message carries no data and is not
encrypted (the connector logs "Could not decrypt message, trying with plain text"), so
each run fetches what its viewer may see. Runs take turns (a lock), so a push arriving
during the periodic run cannot notify a change twice. Without a distributor, or with the
ntfy app on another server, there is no push and the periodic job carries on alone.

To test the phone's side without an alert, post to the address (logged as
`StatusPush: ... for <address>` when the hub refuses it, or found in the ntfy app's
subscriptions): `curl -d refresh '<address>'`, then watch
`adb logcat | grep -E 'StatusPush|RefreshWorker'`.

Testing on a device: a periodic job cannot be forced early (WorkManager reschedules a
run that comes "before schedule"). With a debug build, write a state into
`files/last-states.json` (`adb shell run-as dev.rootiest.status ...`) and wait for the
next run.

## Data source

`GET https://status.rootiest.dev/api/status`, served by rootiest-hub (the status page's
server, whose repository is not public). The body is rootiest-hub's `View` struct
encoded as JSON, so field names are Go's exported names
(`Level`, `Groups`, `Services`, `Bar`...). It depends on the viewer: a signed-out
request gets only what is public.

`core/.../data/StatusModels.kt` declares only the fields the app shows and the parser
ignores the rest, so new server fields never break the app. A renamed or removed field
does: when rootiest-hub changes `View`, update the models and refresh the test fixture.

States and their colours (`ui/theme/Theme.kt`, `toneOf`):

| Colour | Levels and states |
|---|---|
| Green | `ok`, `up`, `healthy` |
| Yellow | `warn`, `standby`, `degraded` |
| Red | `bad`, `down`, `unhealthy` |
| Grey | anything else; `none` (no data) in the uptime strip |

## Architecture

Two modules, package `dev.rootiest.status`, Kotlin with Jetpack Compose and Material 3:

- `:core` (an Android library) holds everything under `data/`: the API's models and
  clients, the saved snapshot and settings, and the plain logic over a snapshot (problems,
  changes, the widget's and the tile's content). It has no UI, so the Wear OS app can
  share it.
- `:app` is the phone app: the screens, the widget, the tile, notifications and the
  background job.
- `:wear` is the Wear OS app: a tile and a list, fed by the phone over the Data Layer
  (see [Wear OS](#wear-os)).

| File | Role |
|---|---|
| `data/StatusModels.kt` | `@Serializable` data classes for the API's JSON |
| `data/StatusApi.kt` | OkHttp GET plus kotlinx.serialization parse, on `Dispatchers.IO` |
| `data/Lookups.kt` | plain functions over a snapshot: find a service, its events, a day's summary text |
| `StatusViewModel.kt` | holds `StatusUiState` (last view, loading, error), the appearance, service list, times and interval settings and the muted names as `StateFlow`s; fetches on start and on `refresh()` |
| `Routes.kt` | Navigation 3 keys: `Home`, `ServiceDetail(group, name)`, `Settings`, `WatchList`, `Licenses`, `SupportTickets`, `SupportTicket(id)`, `NewSupportTicket(prefill)` |
| `ui/App.kt` | the app root: `StatusViewModel` and `SupportViewModel` above the navigation, the back stack and `NavDisplay` |
| `ui/StatusScreen.kt` | the list: top bar, pull-to-refresh, headline, servers, groups, tappable services |
| `ui/ServiceScreen.kt` | a service's detail: summary, recent changes, daily uptime |
| `ui/SettingsScreen.kt` | settings: notifications, push status, the background check, appearance, service list, times, about |
| `ui/WatchListScreen.kt` | the services and servers to notify about, one switch each |
| `ui/LicensesScreen.kt` | the open-source licenses, from the list the AboutLibraries plugin generates |
| `ui/Components.kt` | pieces both screens use: error banner, section title and sticky section header, dot, uptime strip |
| `data/Problems.kt` | what counts as a problem: what the widget lists, and the list arranged by the Service list setting |
| `data/StatusCache.kt` | the last good snapshot, saved for the widget and the tile |
| `data/Settings.kt` | `Appearance`, `ServiceListMode`, `RefreshInterval` and `SettingsStore` (the saved settings: appearance, service list, interval, muted names) |
| `data/Auth.kt` | proof and challenge, reading a token, `AuthStore` (a saved sign-in: `forStatus`, `forSupport`) |
| `data/Tickets.kt` | the support desk's ticket models and `SupportApi` (list, thread, form, create, reply, close; `SignInNeeded`, `DeskRefused`) |
| `SupportViewModel.kt` | `SupportUiState`: the desk's sign-in, the ticket list, the open thread, sending replies, closing and opening tickets |
| `ui/SupportScreen.kt` | Support: the ticket list (or the sign-in prompt) and a ticket's thread with its reply box |
| `ui/NewTicketScreen.kt` | opening a ticket: the desk's form, with its choices for this person |
| `SupportReplyWorker.kt` | the desk pushed `ticket <id>`: fetch it and post the Support replies notification |
| `ui/Markdown.kt` | a message's Markdown as styled text (commonmark-java) |
| `widget/StatusWidget.kt` | the Glance widget: fetch or cached snapshot, two layouts by size |
| `widget/StatusWidgetReceiver.kt` | the widget's receiver; schedules the background job |
| `widget/RefreshWorker.kt` | the background job: fetch, notify changes, redraw widgets, at the chosen interval and on push |
| `data/Times.kt` | `TimeFormat` (relative or clock; default, ISO or custom format; 12 or 24 hours; local or UTC) and `strftime` |
| `data/Changes.kt` | what changed between two fetches, less the muted names |
| `data/Damping.kt` | `NotifyAfter` and `damp`: outages held until they have lasted the Settings delay |
| `data/WidgetContent.kt` | `WidgetScope` and `widgetContent`: a widget's headline and rows for everything, one group or the servers |
| `data/Watch.kt` | `WatchSnapshot` (the Data Layer path, key and JSON), `watchSummary` and `watchComplication`: what the watch tile and complication show |
| `Watch.kt` | sends each new snapshot to the watch over the Wearable Data Layer |
| `wear/.../StatusTile.kt`, `StatusComplication.kt`, `SnapshotListener.kt`, `Snapshots.kt`, `StatusActivity.kt` | the watch app: its tile, its complication, receiving snapshots, its saved copy, its list screen |
| `data/TileContent.kt` | `tileContent`: the tile's subtitle and level, from the Everything widget's content |
| `StatusTileService.kt` | the Quick Settings tile: reads the saved snapshot, opens the app or its Settings |
| `widget/WidgetConfigActivity.kt` | the screen that chooses what one widget shows; `WidgetStore` keeps the choice per widget |
| `Notifier.kt` | the two notification channels, permission and channel checks, the phone's settings pages, posting with a Mute button |
| `MuteReceiver.kt` | a notification's Mute button: mutes the name and clears the notification |
| `Battery.kt` | the app's battery setting (restricted, optimized, unrestricted) and the page that changes it |
| `Push.kt` | `StatusPushService` (UnifiedPush events), the saved push address and last push time, `pushStatus` |
| `ui/theme/Theme.kt` | Catppuccin Mocha and Latte palettes, the current one (`palette`), Material colour schemes, state colours |
| `MainActivity.kt` | edge-to-edge activity that hosts the screen; applies the appearance setting |

Navigation uses Navigation 3: the back stack is a list of `@Serializable` keys that
survives process death. Screens slide horizontally on open, back and the back gesture
(Navigation 3 would fade, and shrink the screen during the gesture). Both screens read the one `StatusViewModel`, so a detail screen
shows the same snapshot as the list, and refreshing either refreshes both. A service is
identified by group and name; if it leaves the catalog while its screen is open, the
screen says so instead of crashing.

The launcher icon is the Rootiest Status icon from rootiest-icons (`rootiest-status`: the
infra-cyan swirl with the heartbeat emblem) on Mocha base, with a monochrome version for themed
icons; the status-bar icon (`ic_stat_status`, also the tile's and complication's fallback) is the
heartbeat alone. It never shows the status, which a launcher icon cannot change; the widget, tile
and notifications do. The phone and watch share the same drawables, written from
`icons/color/rootiest-status.svg` and `emblems/status.svg`: redo them when that icon changes
(rootiest-icons `docs/deployments.md`).

## Build configuration

- Versions live in `gradle/libs.versions.toml`. AGP 9 compiles Kotlin itself, so the
  build applies only the Compose and serialization Kotlin plugins, plus AboutLibraries
  (`com.mikepenz.aboutlibraries.plugin.android`), which collects the dependencies'
  licenses for Settings > About.
- `compileSdk`/`targetSdk` 37, `minSdk` 29 (Android 10).
- Application id `dev.rootiest.status`, for the watch app too. The version is
  `appVersion` in the root `build.gradle.kts`, shared by both apps; `versionCode`
  follows from it (`1.2.3` is 10203).
- `./gradlew install*` installs for the phone's main user only (`--user 0`), not into a
  work profile or Private space; `adb install` without it installs into every profile.
- Release builds are minified with R8 and shrink unused resources, and are signed with
  the release key when the machine has it (see Releases), otherwise with the debug key.
- Use release builds to judge how the app feels. On a Pixel 9 Pro the list scrolled with
  13% janky frames in a debug build and 1% in a release build.

## Tests

`./tests/run-tests.sh` runs `:core`'s unit tests and `verifyRoborazziDebug` (`:app`'s `testDebugUnitTest` with screenshot
comparison): JVM tests, no device needed, including the screen and screenshot tests below.
The `data/` tests live in `core/src/test`, with the JSON fixtures in
`core/src/test/resources` (the app's screen tests read the same files). `StatusApiTest` parses `status-sample.json`, a saved signed-out
answer from the live API, and checks that unknown fields are ignored and the backup line parses and follows the time setting. `ProblemsTest`
covers what the widget lists and the Service list arrangements, `ChangesTest` what counts as a change and muting, `DampingTest` the outage delay, `TimesTest` the time formats and strftime fields, `WidgetContentTest` what each widget choice shows, `SupportTest` what Report a problem fills in, `AuthTest` the challenge (the same test vector as rootiest-hub's) and reading a token, `TicketsTest` the support desk's answers (`support-tickets.json`, `support-thread.json`, `support-form.json` and the refusal `support-422.json`, written by rootiest-hub's own API handlers), how the client sends the sign-in and each request's JSON, and how it reads refusals, `MarkdownTest` how messages are drawn and which links open. `LookupsTest` runs
the `data/Lookups.kt` functions against the same fixture (it relies on the fixture's
Notes outage on 2026-10-27, so keep an answer with a service event when refreshing it). Refresh the
fixture when the API changes:

```sh
curl -sSf https://status.rootiest.dev/api/status > core/src/test/resources/status-sample.json
```

### Screen tests

Tests under `app/src/test/java/dev/rootiest/status/ui/` draw the real Compose screens on the
JVM with Robolectric and check what is on screen with Compose's test rule
(`createComposeRule()`), so they run in the same `testDebugUnitTest` with no device or
emulator. `StatusScreenTest` covers the status list: signing in shows the Servers section at
the top (#32), Problems only with nothing down says so, a section's title sticks while its rows
scroll, and a failed refresh keeps the list under its error banner. `SupportScreenTest`
covers Support: the sign-in prompt and its link to create a RootiestID, no tickets, the list with whose turn it is, a thread with its
Markdown drawn and staff replies from Rootiest Support, the reply box (its text kept until the
desk takes the reply), closing, and the new-ticket form (Report a problem's prefill, the desk's
refusals, a hidden service left out).

To write one, give the screen a fixed state (`StatusScreen` takes a whole `StatusUiState`, so no
view model or network is involved), wrap it in `RootiestTheme`, then find nodes by their text
(`onNodeWithText`) and assert or act on them (`assertIsDisplayed`, `performScrollToNode`). To
change the state mid-test, hold it in `mutableStateOf` and call `waitForIdle()` after each change.
Check that a new test fails when the behaviour it guards is broken.

### Screenshot tests

`ScreenshotTest` draws the status list (signed out and signed in as staff with a server and a
service in trouble, dark and light), the Notes detail screen, Settings, Support (the ticket list, a thread, and the new-ticket form as Report a problem fills it), and the widget (each
choice: everything, the servers, one group, and the small layout) and compares them with the
golden images in `app/src/test/screenshots/`, using [Roborazzi](https://github.com/takahirom/roborazzi).
`./tests/run-tests.sh` runs `verifyRoborazziDebug`, which is `testDebugUnitTest` with the
comparison on, so any pixel that changed fails it. After a deliberate change to how something
looks:

```sh
./gradlew recordRoborazziDebug   # rewrite the goldens
git diff --stat app/src/test/screenshots   # and look at the new images before committing them
```

When a comparison fails, `app/build/outputs/roborazzi/` holds each failing image as drawn
(`*_actual.png`) and a side-by-side with the differences marked (`*_compare.png`); CI uploads
that folder as the `screenshot-diffs` artifact.

What the images can and cannot show:

- Times are fixed: the screens get a 24-hour UTC clock and the test sets the JVM's zone to UTC
  (the widget reads the phone's zone), so the images match on every machine.
- The widget is drawn by Glance into the same `RemoteViews` a launcher gets, from a snapshot
  saved in `StatusCache`, so no network is used. Robolectric does not round views the way
  Android 12+ launchers do, so its dots and corners come out square in the images.
- Screens are captured at 360×740 dp, so only what fits is compared: Settings' Version row
  is out of the picture, and a version bump does not change any golden.

Setup notes, in `app/build.gradle.kts`: the tests need the app's resources
(`isIncludeAndroidResources`); Espresso is pinned to 3.7.0 because the one Compose's test library
brings fails on Android 16 and later; and the test JVM gets `--add-exports`/`--add-opens` flags
because Robolectric reaches into JDK internals that JDK 25 closes by default.

## Releases

Releases are built and signed on the maintainer's machine, which holds the release key,
and published as Gitea releases with the APK attached. The key never leaves that machine
and CI holds no secrets.

- **The key**: a PKCS12 keystore (alias `rootiest-status`, RSA 4096, valid until 2126)
  with its passwords in a properties file beside it. Certificate SHA-256
  `36:15:BB:5F:65:09:81:1F:A5:BB:82:83:03:B5:72:64:2C:36:3E:40:5A:F9:3D:47:75:23:27:9A:D0:EC:93:A4`.
  Both files are backed up off the machine: an update must be signed with the same key,
  so losing it would mean every phone has to uninstall and reinstall.
- **Gradle** finds the key through `rootiest.signing=<path to the properties file>`
  (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) in `~/.gradle/gradle.properties`.
  Without it, as in CI or on a contributor's machine, release builds get the debug key.
- **To release**: bump `appVersion` in `build.gradle.kts` through a PR, merge it,
  then on an up-to-date `main` run `scripts/release.sh`. It refuses a dirty tree, a
  `main` that differs from `origin/main`, and a tag that exists; runs the tests; builds
  the release APKs; refuses one not signed with the release certificate; and creates the
  Gitea release `v<appVersion>` (the tag on the current commit) with
  `rootiest-status-<version>.apk` (the phone) and `wear-rootiest-status-<version>.apk`
  (the watch) attached and the pull requests merged since the last
  release as notes. `scripts/release.sh --dry-run` does all of it but publishing.
- **Installing and updates**: Obtainium, with its Forgejo source (Gitea serves the same
  releases API) and the repository URL `https://git.rootiest.dev/rootiest/rootiest-status-android`.
  No token is needed: the repository is public. Obtainium then offers each new release.
  Each release carries two APKs, so set the app's **Filter APKs by regular expression**
  to `^rootiest-status-[0-9]` in Obtainium, or it asks which one to install.
- A build signed with another key (a debug build, or a release build from a machine
  without the key) cannot update an installed release, and the reverse: Android
  refuses the signature. Switching between them means uninstalling first, which loses
  the sign-in and removes the widget.

## CI

`.github/workflows/ci.yml` is a GitHub-only workflow: the Android build needs the SDK
and about 2 GB of Gradle heap, more than the Gitea runners are sized for. It runs on every
push the Gitea push mirror copies to GitHub, and on demand. It runs the tests, builds
the debug APK and uploads it as the `app-debug` artifact. Results appear on GitHub,
not on the Gitea pull request.

## Roadmap

1. Fetch and render the live status. Done.
2. Service detail screens and navigation. Done.
3. Home-screen widget (Glance) refreshed by WorkManager. Done.
4. Notifications on state changes. Done.
5. Sign in with RootiestID to see the Servers section. Done, through rootiest-hub's
   app sign-in rather than a separate RootiestID client.
6. Signed release APKs on Gitea releases, installed and updated with Obtainium. Done.
