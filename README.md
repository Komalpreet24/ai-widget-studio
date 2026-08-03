# AI Widget Studio

An Android app that turns natural language into functional home screen widgets. Describe what you want, and AI generates a working widget with live data, tap actions, and conditional styling.

**"Show my missed calls from today"** → A widget that displays your missed call count, updates every 15 minutes, and turns red when you have unread calls.

---

## How It Works

1. Describe a widget in plain English
2. AI converts your description into a structured JSON DSL
3. The app parses, validates, and renders it as a real Android widget
4. The widget lives on your home screen with live data and interactive buttons

---

## Features

**Live Preview**  
See your widget render in real-time as the AI generates it. The preview shows actual data from your device so you know exactly what you'll get before adding it to your home screen.

**Iterative Refinement**  
Don't like something? Send another prompt. "Make the text bigger", "Add a reset button", "Change the color to blue when count is zero". The AI modifies the existing widget based on your feedback.

**Conditional Styling**  
Widgets change appearance based on state. "Turn red when missed calls > 0", "Show green background when water intake reaches 8 glasses". Conditions are evaluated on every render.

**Daily/Periodic Resets**  
Counter widgets can auto-reset. "Reset at midnight", "Reset every 2 hours". The state engine tracks last reset time and applies resets when due.

**Live Data Sources**  
Widgets pull real data from Android system APIs: missed call count and caller names from CallLog, next calendar event from CalendarContract, app screen time from UsageStatsManager.

**Offline Support**  
Works without internet using an on-device LLM. Download once, generate widgets anywhere.

---

## AI Models

The app supports two generation modes.

### Cloud Model (Gemini)

Uses `gemini-3.5-flash` via Gemini Android SDK. JSON streams in real-time as it generates. `responseMimeType = "application/json"` ensures valid output. Temperature is set to 0.2 for deterministic responses. Theme hints and installed apps list are injected into every prompt to prevent hallucinated package names.

### Local Model (On-Device)

Uses MediaPipe LLM Inference with LiteRT backend. Default model is Qwen2.5-1.5B-Instruct (quantized, ~1.5GB). One-tap download from HuggingFace in Settings, or import your own `.task` or `.litertlm` file. Once downloaded, works completely offline. Faster for simple widgets, less accurate for complex prompts.

Switch between modes in Settings.

---

## Demo Prompts

| Prompt | What You Get |
|--------|--------------|
| "Water intake tracker with + and - buttons, resets daily at midnight" | Counter widget with increment/decrement, auto-resets at 00:00 |
| "Show my next calendar event and minutes until it starts" | Live calendar widget with countdown |
| "Instagram screen time today in minutes" | Usage stats widget querying UsageStatsManager |
| "Missed calls widget that turns red when count > 0" | Conditional styling based on runtime state |
| "Quick launch buttons for Spotify, YouTube, and Chrome" | Row of app launcher buttons |

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
│  On-device blocklist check (instant)                            │
│  Gemini feasibility check (async, non-blocking)                 │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  WidgetGenerator (Cloud or Local)                                │
│  GeminiWidgetGenerator: streaming, JSON mode, context hints     │
│  LocalWidgetDslGenerator: MediaPipe LLM, offline capable        │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  WidgetDslProcessor                                              │
│  WidgetDslParser: kotlinx.serialization, lenient mode           │
│  WidgetDslMapper: DTO to domain model, tolerant parsing         │
│  WidgetValidator: warnings, not errors                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  WidgetRuntime                                                   │
│  Creates/updates widgets with Mutex for thread safety           │
│  Manages state transitions (increment, reset, toggle)           │
│  Persists to Room (WidgetEntity + WidgetStateEntity)            │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  Jetpack Glance (WidgetGlanceAppWidget)                         │
│  Renders UiNode tree recursively                                │
│  Resolves data sources synchronously before first paint         │
│  Applies conditional style overrides through entire subtree     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  WidgetDataRefreshWorker (WorkManager)                          │
│  Runs every 15 minutes                                          │
│  Runs immediately on app start                                  │
│  Runs after permission grant                                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Sources

| Source | What It Provides | Example Variable |
|--------|------------------|------------------|
| `CALL_LOG` | Missed call count, caller names | `missed_count`, `missed_caller_1` |
| `CALENDAR` | Next event title, minutes until start | `next_event`, `event_minutes` |
| `USAGE_STATS` | Screen time per app in minutes | `instagram_time` |

`DataSourceResolver` dispatches to the correct resolver based on variable name patterns. Supports indexed access (`_caller_1`, `_caller_2`) for multiple values.

---

## The DSL

AI outputs JSON like this:

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

The mapper is lenient. `"COL"` or `"COLUMN"` both work, `">"` or `"GT"` both work. This tolerance is intentional because AI output isn't always consistent.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose, Material 3 |
| Widgets | Jetpack Glance |
| DI | Hilt with @EntryPoint for Glance/Worker contexts |
| Database | Room with 3 entities and 2 migrations |
| Background | WorkManager with HiltWorkerFactory |
| Cloud AI | Gemini Android SDK with streaming and JSON mode |
| Local AI | MediaPipe LLM Inference, LiteRT |
| Serialization | kotlinx.serialization with lenient parsing |
| State | StateFlow, DataStore |
| Concurrency | Coroutines, Mutex for thread-safe state updates |

---

## Building

1. Clone the repo
2. Add your Gemini API key to `local.properties`:
   ```
   GEMINI_API_KEY=your_key_here
   ```
3. Build and run on a device/emulator with API 26+

For offline-only usage, skip the API key and download the on-device model from Settings.
