# Architecture

This document describes the platform core of ComposeFoundry and the roadmap toward
becoming a general-purpose Android UI artifact preview platform.

## Goal

Preview arbitrary Android UI artifacts (JSON DSL, Android XML layouts, and eventually
Compose code, APK/AAB resources, project trees) through a single normalized model and
a pluggable set of format parsers.

## Core building blocks

| Type | Module | Responsibility |
|------|--------|----------------|
| `UiArtifact` | `core:ui-plugin-sdk` | Unifies a file / folder / APK / snippet into one input abstraction. |
| `UiFormatPlugin` | `core:ui-plugin-sdk` | Contract for a format: `canHandle(artifact)` → `PluginMatch`, `parse(artifact, context)` → `ParseResult`. |
| `PluginDescriptor` | `core:ui-plugin-sdk` | Declares id, version, supported extensions/MIME, `capabilities`, `previewLevels`, `priority`. |
| `ArtifactDetector` | `core:ui-plugin-sdk` | Infers `ArtifactKind` from content structure / MIME / extension / magic bytes before plugin matching. |
| `PluginManager` | `core:ui-plugin-sdk` | Registers plugins; selects the best match by capability filter + score then priority (thread-safe). |
| `UiGraph` | `core:ui-model` | Normalized UI tree: `UiNode` / `UiValue` / `UiModifier` / `Diagnostic` / `ThemeSpec`. |
| `Diagnostic` | `core:ui-model` | Cross-format diagnostic with `severity`, `sourcePlugin`, `location`. |
| `ParseResult` | `core:ui-plugin-sdk` | `Success` / `Partial` / `Failed`, each carrying a `UiGraph` and diagnostics. |
| `ComponentRegistry` | `app` | Maps a component `type` → `ComponentRenderer` (legacy renderers, synchronized). |
| `RendererManager` | `app` | Facade that wires `ComponentRegistry` + `PluginManager`; initialized once in `FoundryApplication`. |
| `DiagnosticsEngine` | `app` | Collects and formats diagnostics from all sources into one panel. |

## Data flow

```
input artifact
   │
   ▼
ArtifactDetector.detect(artifact)  ──▶  artifact.detectedKind
   │  content structure / MIME / extension / magic bytes
   ▼
PluginManager.selectFor(artifact, requires = [RENDER_INTERACTIVE])
   │  capability filter + score/priority
   ▼
UiFormatPlugin.parse(artifact, PreviewContext)  ──▶  ParseResult(graph, diagnostics)
   │
   ▼
UiGraphAdapters.toUiDocument(graph)
   │  UiGraph ──▶ UiDocument (Strangler bridge)
   ▼
ComponentRenderer (legacy Compose renderers)
```

`UiGraph` is currently bridged back to the legacy `UiDocument`/`UiElement` renderer so
existing JSON DSL rendering is preserved while the platform grows. The bridge loses
some information (e.g. only the first `UiModifier` and first theme are used) and will be
reduced over time as renderers consume `UiGraph` directly.

## Plugins shipped today

- **`AndroidUiJsonPlugin`** — parses the `.androidui.json` DSL via `UiParser`/`UiValidator`.
- **`AndroidUiXmlPlugin`** — converts Android XML layouts to `UiGraph` via `XmlLayoutParser`.

## Known limitations (current)

1. **`UiGraph` is not yet the direct render source** — partially mitigated: `PreviewSurface` now receives the normalized `UiGraph` and feeds `ComponentRenderer` via a thin `toUiElement` adapter (see limitation #1 history). Next step is to make `ComponentRenderer` consume `UiNode` directly so the adapter can be removed.
2. ~~**Plugin matching was lenient**~~ — now mitigated by `ArtifactDetector`, which fills `artifact.detectedKind` from content structure / MIME / extension before `PluginManager` selects (see limitation #2 history). Plugins still keep a lenient substring fallback for robustness.
3. **XML preview is low-guarantee** — partially mitigated: `XmlLayoutParser` now emits `WARNING` diagnostics for downgraded tags and ignored attributes, and `AndroidUiXmlPlugin` resolves `@string` / `@color` / `@dimen` references when a `ResourceTable` is supplied via `PreviewContext.resourceTable` (otherwise it keeps the reference and emits an `INFO` diagnostic). Cross-file resource merging (project-level) is still future work.
4. **No project-level preview** — a whole Android project (manifest, modules, resource merge) is not yet indexed.
5. **core modules are Android libraries** — `ui-model` / `ui-plugin-sdk` do not depend on the Android framework and could become plain JVM / KMP modules for reuse in CLI / desktop / server.

## Roadmap

### Stage 1 — Stabilize the platform (current)
- Reliable release build (R8 / serialization / Compose keep rules).
- CI runs unit tests and blocks merge on failure.
- `RendererManager` initialized in `Application`.
- `ComponentRegistry` thread-safe.
- Documentation (this file + README).

### Stage 2 — Tighten formats
- `ArtifactDetector` layer: infer `ArtifactKind` from extension, MIME, magic bytes, and structure.
- Stricter `canHandle` (parse JSON / inspect XML root tag instead of substring checks).
- XML diagnostics that explain downgrades and unresolved resources.
- Resource resolution (`strings.xml`, `colors.xml`, `dimens.xml`, then theme/style/qalifiers).

### Stage 3 — `UiGraph` as render source (in progress)
- [done] Render entry (`PreviewSurface`) now takes the normalized `UiGraph` and feeds `ComponentRenderer` via a thin `toUiElement` adapter — `UiGraph` is the single render source; `UiDocument` is kept only for codegen / a11y / JSON serialization.
- `UiGraphPreview(graph)` renderer consuming `UiNode` directly.
- Migrate component mapping from `UiElement` to `UiNode`.
- Remove the `UiGraph → UiDocument → UiElement` adapter once renderers consume `UiNode`.

### Stage 4 — Project & advanced formats
- `core:ui-project` + `plugin-android-project`: scan modules, manifest, resource merge, navigation graphs → `ProjectUiGraph`.
- Compose code preview (composable recognition → static UI graph → sandboxed execution).
- APK / AAB resource extraction and preview.
