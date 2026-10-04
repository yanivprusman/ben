@AGENTS.md

# Ben

A phone screen that shows **one conversation and nothing else**: the one Yaniv
holds with Ben by voice, through the wake phrase. What the phone heard on the
right, what Ben answered on the left, live as it happens.

Android package `com.automatelinux.ben.dev`, launcher name **Ben**.

## It has no data of its own

```
voiceControl app ──POST /api/command──▶ voiceControl server :3143 ──▶ Ben (openclaw)
                                             │ writes every turn to
                                             ▼ /var/lib/automatelinux/voicecontrol/conversation.jsonl
Ben app ◀──────GET /api/conversation─────────┘
```

The conversation is kept by the server that relays it (`/opt/dev/voiceControl`,
see "The conversation is kept" in its CLAUDE.md). This app reads it from there,
directly at `10.7.0.2:3143` over WireGuard — never through nginx, and **not
through this repo's own Next.js server**, which exists only because the scaffold
makes one (it hosts the feedback routes; its root page is empty on purpose, like
every phone-only app here).

So a change to *what is recorded* is a change in voiceControl, not here.

## The token can read and nothing else

`mobile/.env` (gitignored) is baked into the APK:

```
API_BASE_URL=http://10.7.0.2:3143/
API_TOKEN=<VOICE_CONTROL_READ_TOKEN from /etc/automatelinux/voicecontrol.env>
```

It must be the **read** token. `VOICE_CONTROL_TOKEN` hands text to an agent
running as root; a transcript viewer has no business carrying it. A build with
no token, or a stale one, says so on screen ("The desktop refused this app") —
it does not look like being offline.

## How it stays current (`shared/…/data`)

- `Window.kt` — the run of events the phone holds, and every decision about what
  to keep. Pure, and where the tests are. The rules that matter:
  - **A different `epoch` empties the window.** The record was replaced; waiting
    for seq 9000 of a file that now has 12 lines would wait forever.
  - **A hole empties it too.** A transcript with lines silently missing is worse
    than one that reloads.
  - **Away for more than `MAX_CATCH_UP` events, start from the latest page**
    instead of paging through days nobody asked to see.
- `Conversation.kt` — `follow()` is the whole sync: ask for what is new, and when
  there is nothing, let the server hold the request (25 s) until there is. It
  runs only while the app is on screen. **The first request after opening never
  waits** — otherwise "Connecting" sits there for 25 seconds whenever nothing is
  new.
- The newest 400 events are saved on the phone, so the app opens on the
  conversation even when the desktop cannot be reached, and says that it is
  showing a saved copy.

## The screen (`shared/…/ui`)

- The list is laid out **newest-first from the bottom** (`reverseLayout`). It
  opens on the latest line; an answer arriving grows upward from the bottom edge;
  older pages are added at the far end, where they cannot push what is being
  read. A top-down list needed scroll-anchoring tricks for all three.
- **"Following" is where he left the list, not where it is this frame** — a new
  line moves the list off the bottom before anything can react to it. Following:
  scroll to it. Reading further up: leave the list alone and put a dot on the
  jump button.
- A sentence with no answer is "Working on it" only while the server can be
  heard and the turn is younger than the server's own limit (`TURN_LIMIT_MS`).
  A saved copy from hours ago must not show Ben working.
- Android 13+ confirms a copy by itself; the app's own "Copied" note is only for
  older versions (`platformConfirmsCopy`).

## Design

*A spoken conversation, written down.* Ink and paper and one colour: the coral
is Ben's voice and nothing else — his mark beside each answer, the trace that
moves while he works, the lamp when the line is open. What was heard is sans in
a bubble (Instrument Sans); what he said is serif on the page (Newsreader). Both
OFL, cut to static weights from the variable fonts; licences in
`mobile/app/licenses`.

The icon is a **B made of two speech bubbles** — what was heard above, Ben below
with a tail and the three-bar voice trace cut through. The same three bars are
`VoiceBars` in the app. Keep them the same shape.

## Build, test, install

Build on the **desktop**, never the leader.

```bash
cd mobile
./gradlew :shared:testDebugUnitTest      # window rules, sync loop, turns, formatting
./gradlew :app:assembleDevDebug          # dev flavour only
androidDeploy ben                        # chunked install over WireGuard + launch
```

**Do not test by sending sentences to the real server** — `POST /api/command`
goes into his conversation and into Ben's memory. To see states that are hard to
produce (a turn in flight, a failure, a long history, a replaced record), run a
second voiceControl server on a scratch port with `VOICE_CONTROL_DATA_DIR`
pointed at a made-up `conversation.jsonl`, bake that URL into a throwaway build,
and install it on the desktop emulator (`phone_demo_36`), not the phone.
