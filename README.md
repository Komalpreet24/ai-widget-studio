# AI Widget Studio

An Android app that turns natural language into functional home screen widgets. Type what you want, and Gemini AI generates a working widget — complete with live data, tap actions, and conditional styling.

**"Show my missed calls from today"** → A widget that displays your missed call count, updates every 15 minutes, and turns red when you have unread calls.

---

## What It Does

1. You describe a widget in plain English
2. Gemini converts your description into a structured JSON DSL
3. The app parses, validates, and renders it as a real Android widget
4. The widget lives on your home screen with live data and interactive buttons

No drag-and-drop. No templates. Just describe what you want.

---

## Demo Prompts

| Prompt | What You Get |
|--------|--------------|
| "Water intake tracker with + and - buttons, resets daily at midnight" | Counter widget with increment/decrement, auto-resets at 00:00 |
| "Show my next calendar event and minutes until it starts" | Live calendar widget using CalendarContract.Instances |
| "Instagram screen time today in minutes" | Usage stats widget that queries UsageStatsManager |
| "Missed calls widget that turns red when count > 0" | Conditional styling based on runtime state |
| "Quick launch buttons for Spotify, YouTube, and Chrome" | Row of app launcher buttons with real package names |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Prompt                              │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  CapabilityChecker                                               │
│  - On-device blocklist check (instant)                          │
│  - Gemini feasibility check (async, non-blocking)               │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  GeminiWidgetGenerator                                           │
│  - System prompt from assets/prompt_online.md                   │
│  - Injects theme hint (LIGHT/DARK/SYSTEM)                       │
│  - Injects installed apps list (prevents hallucinated packages) │
│  - Streams JSON response with temperature=0.2                   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  WidgetDslProcessor                                              │
│  ├── WidgetDslParser (kotlinx.serialization, lenient mode)      │
│  ├── WidgetDslMapper (DTO → Domain model)                       │
│  └── WidgetValidator (warnings, not errors)                     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  WidgetRuntime                                                   │
│  - Creates/updates widgets with Mutex for thread safety         │
│  - Manages state transitions (increment, reset, toggle)         │
│  - Persists to Room (WidgetEntity + WidgetStateEntity)          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  Jetpack Glance (WidgetGlanceAppWidget)                         │
│  - Renders UiNode tree recursively                              │
│  - Resolves data sources synchronously before first paint       │
│  - Applies conditional style overrides through entire subtree   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  WidgetDataRefreshWorker (WorkManager)                          │
│  - Runs every 15 minutes                                        │
│  - Runs immediately on app start (handles reinstall edge case)  │
│  - Runs after permission grant                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Sources

The DSL supports live data from Android system APIs:

| Source | What It Provides | Permission |
|--------|------------------|------------|
| `CALL_LOG` | Missed call count, caller names (supports `_caller_1`, `_caller_2` for Nth caller) | `READ_CALL_LOG` |
| `CALENDAR` | Next event title, minutes until start (uses `Instances.CONTENT_URI` for recurring events) | `READ_CALENDAR` |
| `USAGE_STATS` | Screen time per app in minutes | `PACKAGE_USAGE_STATS` (Settings toggle) |

Data sources are resolved by `DataSourceResolver` — a single class that dispatches to the correct resolver based on variable name patterns.

---

## The DSL

Gemini outputs JSON like this:

```json
{
  "dslVersion": 1,
  "metadata": { "name": "Missed Calls", "size": "SMALL" },
  "data": {
    "variables": [
      {
        "name": "missed_count",
        "type": "INT",
        "default": 0,
        "source": { "type": "CALL_LOG", "filter": "MISSED", "windowMinutes": 1440 }
      }
    ]
  },
  "actions": [
    { "id": "open", "type": "OPEN_APP" }
  ],
  "conditions": [
    {
      "variable": "missed_count",
      "operator": ">",
      "value": 0,
      "style": { "backgroundColor": "#FFCDD2", "textColor": "#B71C1C" }
    }
  ],
  "ui": {
    "type": "CARD",
    "child": {
      "type": "COLUMN",
      "children": [
        { "type": "TEXT", "value": "📞 {{missed_count}} missed" },
        { "type": "BUTTON", "text": "Open", "action": "open" }
      ]
    }
  }
}
```

The mapper is lenient — `"COL"` or `"COLUMN"` both work, `">"` or `"GT"` both work. This tolerance is intentional because AI output isn't always consistent.

---

## Key Implementation Details

**Why the widget doesn't show stale data on first render:**
`WidgetGlanceAppWidget.provideGlance()` calls `dataSourceResolver.resolveAll()` synchronously before `provideContent`. The fresh values are merged into state and written to Glance's DataStore before the first frame renders.

**Why the worker runs on every app start:**
WorkManager's job queue is stored in its own SQLite database. On app reinstall, that database is wiped — the periodic job disappears. `AppController.onCreate()` calls `RefreshWorkScheduler.runNow()` to guarantee at least one immediate refresh regardless of WorkManager state.

**Why we use `Instances.CONTENT_URI` for calendar:**
`Events.CONTENT_URI` returns base event definitions. A recurring event like "Team standup every Monday" is one row. `Instances.CONTENT_URI` expands each occurrence into its own row with a concrete timestamp — that's how you find the *next* occurrence.

**Why `AppOpsManager` for usage stats permission:**
`UsageStatsManager.queryUsageStats()` returns an empty list when permission is denied, never null. A null check always passes. The correct check is `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS)`.

**Why we inject the installed apps list:**
Gemini hallucinates package names. "TikTok" becomes `com.tiktok.app` instead of `com.zhiliaoapp.musically`. At generation time, we query `PackageManager.queryIntentActivities(CATEGORY_LAUNCHER)` and inject the real `label=package` list into the prompt.

**Why conditional styles are passed through the entire node tree:**
A `RenderCondition` like "turn red when missed_calls > 0" should affect the Card background *and* the Text color inside it. The `conditionOverride` parameter is passed down through every `RenderNode` call. Each node checks `conditionOverride?.backgroundColor` before its own style.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 |
| Widgets | Jetpack Glance |
| DI | Hilt (with `@EntryPoint` for Glance/Worker contexts) |
| Database | Room (3 entities, 2 migrations) |
| Background | WorkManager with `HiltWorkerFactory` |
| AI | Gemini Android SDK (streaming, JSON mode) |
| Serialization | kotlinx.serialization (lenient parsing) |
| State | StateFlow + DataStore |
| Concurrency | Coroutines + Mutex for thread-safe state updates |

---

## Project Structure

```
app/src/main/java/com/example/aiwidgetstudio/
├── ai/                     # Gemini integration, capability checking
│   ├── GeminiWidgetGenerator.kt
│   ├── CapabilityChecker.kt
│   └── AiOutputExtractor.kt
├── data/
│   ├── datasource/         # Live data resolvers
│   │   ├── DataSourceResolver.kt
│   │   ├── CallLogResolver.kt
│   │   ├── CalendarResolver.kt
│   │   └── UsageStatsResolver.kt
│   ├── local/              # Room database
│   └── repository/
├── domain/model/           # Pure Kotlin domain classes
├── engine/
│   ├── parser/             # JSON → DTO → Domain
│   ├── runtime/            # Widget lifecycle management
│   ├── state/              # State machine (increment, reset, toggle)
│   └── ConditionEvaluator.kt
├── glance/                 # Widget rendering
│   ├── WidgetGlanceAppWidget.kt
│   ├── WidgetGlanceRenderer.kt
│   └── BindingResolver.kt
├── presentation/           # ViewModel
├── worker/                 # Background refresh
└── MainActivity.kt
```

---

## Building

1. Clone the repo
2. Add your Gemini API key to `local.properties`:
   ```
   GEMINI_API_KEY=your_key_here
   ```
3. Build and run on a device/emulator with API 26+

---

## Permissions

The app requests permissions only when you create a widget that needs them:

- `READ_CALL_LOG` — for missed calls widgets
- `READ_CALENDAR` — for calendar widgets  
- `PACKAGE_USAGE_STATS` — for screen time widgets (requires Settings toggle)

Permission checks happen at save time. If a required permission is missing, a bottom sheet explains what's needed and why.

---

## Limitations

- `installedAppsHint` is cached for the process lifetime — new app installs won't appear until app restart
- On-device LLM (MediaPipe) is available but less accurate than Gemini for complex prompts
- Widget size is declared at creation time and can't be changed dynamically
- No support for images or custom fonts in widgets (Glance limitation)

---

## What I Learned

- Glance widgets run in a separate process — you can't use normal Hilt injection, you need `@EntryPoint`
- WorkManager's job queue doesn't survive app reinstalls — always have a fallback
- Android's calendar API has two content URIs and they do very different things
- AI output needs defensive parsing — `isLenient = true` and `ignoreUnknownKeys = true` are essential
- Conditional styling in a tree structure requires passing overrides down, not just applying at the root

---

## License

MIT
