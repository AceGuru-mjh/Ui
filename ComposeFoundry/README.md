# ComposeFoundry

A JSON DSL-driven UI preview engine for Android Jetpack Compose.

Write UI layouts in a declarative JSON format (`.androidui.json`), and ComposeFoundry parses, validates, and renders them as real Compose components in real-time.

## Features

- **DSL Rendering Engine** — 17+ supported component types (Column, Row, Box, Text, Button, Card, LazyColumn, Switch, Slider, TabRow, etc.)
- **Component Palette** — 12 pre-built templates for rapid prototyping
- **Undo / Redo** — 50-step history stack
- **Device Preview Presets** — Small Phone / Pixel 7 / Pixel Tablet
- **Dark / Light Theme Toggle**
- **SAF File Import / Export** — Open and save `.androidui.json` files via Android Storage Access Framework
- **Diagnostics Engine** — Real-time parse errors and validation warnings
- **Gradient Support** — Linear, radial, and sweep gradients in DSL
- **Animation Support** — Fade, slide, scale, and bounce entrance animations

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
├── app/src/main/java/com/foundry/
│   ├── preview/
│   │   ├── MainActivity.kt          # Entry point
│   │   ├── dsl/                     # DSL models, parser, validator
│   │   ├── engine/                  # UiRenderer + DiagnosticsEngine
│   │   ├── sandbox/                 # PreviewSurface + ThemeManager
│   │   ├── state/                   # FoundryViewModel
│   │   └── ui/                      # Screens (Main, Editor, Components, Preview)
│   ├── gradient/                    # Gradient parsing & brush building
│   └── animation/                   # Animation parsing & wrappers
├── app/src/main/assets/
│   └── sample_preview.androidui.json
└── build.gradle.kts
```

## Build & Run

```bash
cd ComposeFoundry
./gradlew assembleDebug
```

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **Kotlin:** 17
- **Compose BOM:** 2024.02.00

## Supported Component Types

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
  "animation": "fadeIn:duration=500,easing=linear",
  "animationDelay": "200"
}
```

Supported types: `fadeIn`, `fadeOut`, `slideInLeft`, `slideInRight`, `slideInUp`, `slideInDown`, `scaleIn`, `bounceIn`

## Gradient DSL

Use in `modifier.background`:

```json
{
  "modifier": {
    "background": "linear-gradient(45deg, #FF6200EE, #FF3700B3)"
  }
}
```

Supported: `linear-gradient(angle, colors...)`, `radial-gradient(colors...)`, `sweep-gradient(colors...)`

## License

MIT
