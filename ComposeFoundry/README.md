# ComposeFoundry

A modular, plugin-based **Android UI artifact preview platform**.

ComposeFoundry started as a JSON DSL-driven UI preview engine for Jetpack Compose and is evolving into a platform that can preview arbitrary Android UI artifacts. Every input format (JSON DSL, Android XML layout, and future formats like Compose code or APK resources) is parsed by a **format plugin** into a single normalized **`UiGraph`**, which drives rendering, diagnostics, and tooling behind a unified surface.

Write UI layouts in a declarative JSON format (`.androidui.json`), and ComposeFoundry parses, validates, and renders them as real Compose components in real-time.

## Features

- **Plugin-based format system** — `UiFormatPlugin` + `PluginManager` turn each input format into a swappable plugin; add a format by adding a plugin, not by editing the main flow.
- **Normalized `UiGraph`** — a format-agnostic UI model (`UiNode` / `UiValue` / `UiModifier` / `Diagnostic` / `PreviewLevel` / `Confidence`) shared by every plugin and the tooling layer.
- **Unified diagnostics** — every plugin emits `Diagnostic` entries that are funneled into one `DiagnosticsEngine` panel.
- **DSL Rendering Engine** — 18 supported component types (Column, Row, Box, Text, Button, Card, LazyColumn, Switch, Checkbox, Slider, ProgressIndicator, TabRow, etc.)
- **Component Palette** — 18 component templates + 5 page templates (Login, Profile, Settings, Dashboard, Chat)
- **Multi-Document Tabs** — Edit multiple layouts simultaneously with tabbed workspace
- **Undo / Redo** — 50-step history stack
- **Device Preview Presets** — Small Phone / Pixel 7 / Pixel Tablet
- **Dark / Light Theme Toggle**
- **SAF File Import / Export** — Open and save `.androidui.json` files via Android Storage Access Framework
- **XML Layout Import** — Convert legacy Android XML layouts to DSL via the `AndroidUiXmlPlugin` plugin
- **Auto-Save with DataStore** — Persistent state across app restarts
- **Diagnostics Engine** — Real-time parse errors and validation warnings
- **Gradient Support** — Linear, radial, conic, and 8 preset gradients via `foundry-gradient` library module
- **Animation Support** — Fade, slide, scale, bounce, shake, pulse, rotate entrance animations via `foundry-animation` library module
- **Compose Code Generation** — Export DSL as compilable Kotlin Compose code via `foundry-codegen` library module
- **Accessibility Audit** — WCAG 2.1 AA compliance checking via `foundry-a11y` library module
- **Inspect & Edit** — Inspect element tree, modify attributes, delete elements

> **Status:** platform foundation is in place (plugins + `UiGraph` + diagnostics). It is not yet a finished product — `UiGraph` is still bridged back to the legacy renderer, plugin matching is still lenient, and XML preview is low-guarantee. See `docs/ARCHITECTURE.md` for the roadmap.

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

## Platform Architecture

ComposeFoundry is built on a small platform core so that new Android UI formats can be added without touching the render path.

```
输入文件 ──▶ UiFormatPlugin.parse() ──▶ UiGraph
                                          │
                                          ▼
                              UiGraph ──▶ adapter ──▶ UiDocument
                                          │                │
                                          │                ▼
                                          │         ComponentRegistry (legacy renderers)
                                          ▼
                                  DiagnosticsEngine (unified diagnostics)
```

- **`core:ui-model`** — normalized data model: `UiGraph`, `UiNode`, `UiValue`, `UiModifier`, `Diagnostic`, `ResourceTable`, plus `resolveResources()` / `validate()` / `normalize()` helpers.
- **`core:ui-plugin-sdk`** — plugin contract: `UiFormatPlugin`, `PluginDescriptor`, `UiArtifact`, `PluginMatch`, `ParseResult`, `PreviewContext`, and the `PluginManager` (capability negotiation + score/priority selection).
- **`app`** — the Android app: the legacy `ComponentRenderer` set plus the `UiGraphAdapters` bridge that converts `UiGraph` ↔ `UiDocument`, and the `RendererManager`/`FoundryApplication` wiring.

Currently `UiGraph` is bridged back to the legacy renderer (Strangler pattern) so existing JSON DSL rendering keeps working while the platform grows. The long-term goal is for `UiGraph` to become the direct render source. See `docs/ARCHITECTURE.md`.

## Project Structure

```
ComposeFoundry/
├── app/                                    # Main application module
│   └── src/main/java/com/foundry/preview/
│       ├── MainActivity.kt                 # Entry point
│       ├── FoundryApplication.kt           # One-time renderer + plugin init
│       ├── dsl/                            # DSL models, parser, validator, XML parser
│       ├── engine/                         # UiRenderer + DiagnosticsEngine + ComponentRegistry
│       ├── plugin/                         # UiGraphAdapters + AndroidUiJsonPlugin + AndroidUiXmlPlugin
│       ├── sandbox/                        # PreviewSurface + ThemeManager
│       ├── state/                          # FoundryViewModel (DataStore auto-save)
│       └── ui/                             # Screens (Editor, Preview, Components, Inspect, Code, A11y)
├── foundry-gradient/                       # Library: gradient parsing & brush building
├── foundry-animation/                      # Library: animation parsing & composable wrappers
├── foundry-codegen/                        # Library: Compose Kotlin code generation
├── foundry-a11y/                           # Library: WCAG accessibility auditing
├── core/
│   ├── ui-model/                           # Normalized UiGraph data model + helpers
│   └── ui-plugin-sdk/                      # Format plugin contract + PluginManager
├── settings.gradle.kts
└── build.gradle.kts
```

## Build & Run

```bash
cd ComposeFoundry
./gradlew assembleDebug
```

Run unit tests (also enforced in CI):

```bash
cd ComposeFoundry
./gradlew testDebugUnitTest
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
