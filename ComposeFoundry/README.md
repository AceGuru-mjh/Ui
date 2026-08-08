# ComposeFoundry

A JSON DSL-driven UI preview engine for Android Jetpack Compose.

Write UI layouts in a declarative JSON format (`.androidui.json`), and ComposeFoundry parses, validates, and renders them as real Compose components in real-time.

## Features

- **DSL Rendering Engine** — 18 supported component types (Column, Row, Box, Text, Button, Card, LazyColumn, Switch, Checkbox, Slider, ProgressIndicator, TabRow, etc.)
- **Component Palette** — 18 component templates + 5 page templates (Login, Profile, Settings, Dashboard, Chat)
- **Multi-Document Tabs** — Edit multiple layouts simultaneously with tabbed workspace
- **Undo / Redo** — 50-step history stack
- **Device Preview Presets** — Small Phone / Pixel 7 / Pixel Tablet
- **Dark / Light Theme Toggle**
- **SAF File Import / Export** — Open and save `.androidui.json` files via Android Storage Access Framework
- **XML Layout Import** — Convert legacy Android XML layouts to DSL
- **Auto-Save with DataStore** — Persistent state across app restarts
- **Diagnostics Engine** — Real-time parse errors and validation warnings
- **Gradient Support** — Linear, radial, conic, and 8 preset gradients via `foundry-gradient` library module
- **Animation Support** — Fade, slide, scale, bounce, shake, pulse, rotate entrance animations via `foundry-animation` library module
- **Compose Code Generation** — Export DSL as compilable Kotlin Compose code via `foundry-codegen` library module
- **Accessibility Audit** — WCAG 2.1 AA compliance checking via `foundry-a11y` library module
- **Inspect & Edit** — Inspect element tree, modify attributes, delete elements

## DSL Example

```json
{
  "version": "1.0",
  "theme": {
    "primaryColor": "#FF6200EE",
    "backgroundColor": "#FFF5F5F5"
  },
  "root": {
    "type": "column",
    "modifier": {
      "fillMaxWidth": true,
      "padding": { "all": 16 }
    },
    "children": [
      {
        "type": "text",
        "attributes": { "text": "Hello ComposeFoundry", "fontSize": "24" },
        "modifier": { "padding": { "bottom": 12 } }
      },
      {
        "type": "button",
        "attributes": { "text": "Click Me" },
        "modifier": { "fillMaxWidth": true }
      }
    ]
  }
}
```

## Project Structure

```
ComposeFoundry/
├── app/                                    # Main application module
│   └── src/main/java/com/foundry/preview/
│       ├── MainActivity.kt                 # Entry point
│       ├── dsl/                            # DSL models, parser, validator, XML parser
│       ├── engine/                         # UiRenderer + DiagnosticsEngine
│       ├── sandbox/                        # PreviewSurface + ThemeManager
│       ├── state/                          # FoundryViewModel (DataStore auto-save)
│       └── ui/                             # Screens (Editor, Preview, Components, Inspect, Code, A11y)
├── foundry-gradient/                       # Library: gradient parsing & brush building
├── foundry-animation/                      # Library: animation parsing & composable wrappers
├── foundry-codegen/                        # Library: Compose Kotlin code generation
├── foundry-a11y/                           # Library: WCAG accessibility auditing
├── settings.gradle.kts
└── build.gradle.kts
```

## Build & Run

```bash
cd ComposeFoundry
./gradlew assembleDebug
```

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **Kotlin:** 2.0.20 (JVM target 17)
- **Compose BOM:** 2024.09.00
- **Version:** 1.9.0

## Supported Component Types (18)

| DSL Type | Compose Widget |
|----------|---------------|
| `column` | `Column` |
| `row` | `Row` |
| `box` | `Box` |
| `text` | `Text` |
| `button` | `Button` |
| `spacer` | `Spacer` |
| `card` | `Card` |
| `divider` | `HorizontalDivider` |
| `image` | Placeholder `Box` |
| `textfield` | `OutlinedTextField` |
| `scroll` | Scrollable `Column` |
| `surface` | `Surface` |
| `lazycolumn` | `LazyColumn` |
| `switch` | `Switch` |
| `checkbox` | `Checkbox` |
| `slider` | `Slider` |
| `progressindicator` | `LinearProgressIndicator` |
| `tabrow` | `TabRow` |

## Animation DSL

Add to any element's `attributes`:

```json
{
  "animation.type": "fade_in",
  "animation.duration": "500",
  "animation.delay": "200",
  "animation.trigger": "appear"
}
```

Supported types: `fade_in`, `slide_in_left`, `slide_in_right`, `slide_in_top`, `slide_in_bottom`, `scale_in`, `expand_vertical`, `bounce`, `shake`, `pulse`, `rotate_in`

Optional spring physics: `"animation.damping": "0.5"`, `"animation.stiffness": "1500"`

Powered by the `foundry-animation` library module.

## Gradient DSL

Use in `modifier.background`:

```json
{
  "modifier": {
    "background": "linear(45deg, #FF6200EE 0%, #FF3700B3 100%)"
  }
}
```

Supported syntaxes:
- `linear(angle, color offset%, ...)` — Linear gradient
- `radial(center=0.5,0.5, color offset%, ...)` — Radial gradient
- `conic(angle, color offset%, ...)` — Conic gradient
- `gradient:sunset` — Preset (sunset / ocean / aurora / fire / midnight / candy / forest / royal)
- `#FF6200EE` — Plain solid color

Powered by the `foundry-gradient` library module.

## Sample Files

| File | Description |
|------|-------------|
| `sample_preview.androidui.json` | Default preview layout |
| `sample_login.androidui.json` | Login form with text fields and button |
| `sample_dashboard.androidui.json` | Dashboard with cards, progress, gradient header |
| `sample_animation.androidui.json` | Animation showcase (fade, slide, scale, bounce) |

## Testing

```bash
cd ComposeFoundry
./gradlew test
```

Unit tests cover:
- `foundry-gradient`: GradientParser (linear/radial/conic/presets)
- `foundry-animation`: AnimationParser (all 12 types, spring params, trigger)
- `app`: UiParser (valid/invalid JSON, nesting, defaults)
- `app`: UiValidator (supported types, missing attributes, depth limits)

## License

MIT
