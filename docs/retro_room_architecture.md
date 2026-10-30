# Retro Room: Interactive 3D Background Architecture

Design for an interactive 3D "retro room" desktop background: clicking a tile
on the AGS bar plays a "notebook closing" transition, hides the tiles, and
travels the camera to a point of interest in a persistent 3D scene rendered
behind every window.

## 0. Constraint from prior art

`RetroVHSSucks/plugin/` was a C++ **Hyprland compositor plugin** doing
ship/camera/world-position simulation. It was removed because it wasn't
reliable across real multi-monitor setups — compositor plugins run inside
Hyprland's process, so a bug there risks the whole session, and per-monitor
state was apparently never fully solved.

**Takeaway driving every choice below: nothing in this design touches the
compositor.** All 3D logic lives in ordinary userspace client code. If it
crashes, Hyprland and the rest of the desktop keep running.

## 1. Where the 3D scene lives

**Decision: render the room inside the existing `qs` (Quickshell) process,
using Qt Quick 3D, not a separate engine process (Godot/Unity/raw
GL/Vulkan).**

Why, given the alternatives:

| Option | Verdict |
|---|---|
| Qt Quick 3D inside Quickshell | **Chosen.** Quickshell already runs as a `PanelWindow` with `ExclusionMode.Ignore` and screen-filling anchors (see `ScreensaverComponent` in `shell.qml`) — that's exactly the layer-shell background surface this needs, already proven in this repo. Zero new processes, zero new IPC transport, reuses `qs ipc call` which already exists for screensaver/notifs. |
| Standalone Godot/engine process as a background layer | Rejected. Wayland's background/bottom layer-shell placement (`zwlr_layer_shell_v1`) is a client-side protocol Godot doesn't natively speak. Making it work would mean patching Godot's `DisplayServer` — the same category of fragile native-protocol hacking that killed the old Hyprland plugin, just moved into a different process. |
| Raw OpenGL/Vulkan custom engine | Rejected. Reinventing scene graph, asset import, and animation tooling from scratch for a stylized room is a large, unnecessary build when Qt Quick 3D already ships all of that and is already in the Quickshell runtime (Qt6/QML). |

The "C++ plugin" requirement is satisfied cleanly here: Quickshell/QML
supports loading custom **QML C++ plugins** (`QQmlExtensionPlugin`,
registered as an importable module, e.g. `import RetroRoom 1.0`). Scene
logic that needs to be fast or stateful (camera rig, travel paths, scene
registry) is written in C++ and exposed as QML types, loaded by
`shell.qml` next to the existing `notif`/`overlay`/`lock`/`screensaver`
imports. No separate build/runtime story is needed beyond what Quickshell
already requires.

## 2. Process / layer layout

```
Hyprland (compositor, untouched)
 ├─ qs (Quickshell, Qt6/QML + C++ plugin)      ← background layer-shell surface
 │   ├─ RoomScene (QtQuick3D View3D)            ← the retro room, always rendered
 │   ├─ existing: NotifsOverlay, InfoOverlay,    ← unchanged, higher layers/overlays
 │   │            LockSurface, ScreensaverComponent
 │   └─ IpcHandler { target: "room" }            ← new, see §3
 │
 ├─ ags run config/ags  (AGS/Astal bar, GTK)     ← top layer, unchanged process
 │   └─ tile click handler → plays "notebook     ← local GTK/CSS animation
 │       closing" animation, then on completion:
 │       exec("qs ipc call room travel <id>")
 │
 └─ hyprpaper / hypridle                          ← unchanged
```

Only one new long-lived piece of runtime state: a `RoomScene` `View3D` added
to `shell.qml`, placed in its own `PanelWindow` (or folded into the existing
background panel) with `exclusionMode: ExclusionMode.Ignore` and
screen-filling anchors, same pattern as `ScreensaverComponent` today.

Multi-monitor: render the room only on the primary monitor (mirroring how a
single retro room doesn't need to tile across displays); other monitors keep
`hyprpaper`'s static wallpaper, same as today. This sidesteps the exact class
of multi-monitor bug that killed the old plugin, by never requiring
per-monitor 3D state at all.

## 3. Bar → scene signaling protocol

Reuse the existing `qs ipc call` mechanism (`shell.qml` already exposes
`notifs` and `screensaver` targets this way) — no new transport.

**`shell.qml` gets a new handler:**

```qml
IpcHandler {
    target: "room"

    function travel(anchorId: string): string {
        if (roomScene.transitioning) return "BUSY";
        roomScene.travelTo(anchorId);
        return "OK";
    }
    function reset(): string {
        roomScene.travelTo("home");
        return "OK";
    }
}
```

**AGS bar tile click handler (`config/ags/widget/Bar.tsx` or wherever the
tile lives):**

1. Play the local "notebook closing" animation (GTK/CSS transform — tiles
   scale/rotate down like a laptop lid, purely a bar-side visual, no 3D
   involved).
2. On animation-finished, `exec(["qs", "ipc", "call", "room", "travel",
   anchorId])` and hide the bar/tiles (or let the animation itself end in a
   hidden state).
3. Optionally poll or `qs ipc call room` a `status` function to know when
   the camera travel finished, if the bar needs to re-show tiles
   automatically afterward.

`anchorId` is an opaque string the bar and the C++ plugin agree on (e.g.
`"music"`, `"mail"`, `"terminal"`) — see §4.

## 4. C++ plugin module boundaries

New directory, e.g. `config/quickshell/room/plugin/` (QML module name
`RetroRoom`):

- **`RoomSceneController`** (`QObject`, exposed to QML) — owns the
  `travelTo(anchorId)` entry point, debounces/queues travel requests
  (rejects a new travel while `transitioning`), exposes `transitioning`
  and `currentAnchor` as QML-bindable properties the `IpcHandler` and the
  QML scene react to.
- **`CameraRig`** — C++ class driving the actual dolly: given a current
  `Node3D` transform and a target anchor's transform, builds and plays a
  `QQuick3DAnimation`/property-animation path (position + look-at) over a
  fixed duration with an easing curve. Kept in C++ rather than a QML
  `Behavior`/`Animation` because travel paths may later need curved
  (spline) camera paths, which is awkward to express declaratively.
- **`SceneAnchorRegistry`** — maps `anchorId` strings to named `Node3D`
  markers placed in the scene (authored in Qt Design Studio or hand-written
  QML `Node { objectName: "anchor:music" }`), so adding a new clickable
  destination is "add a marker node in QML + register the id" — no C++
  change needed per-anchor.
- Everything else (materials, meshes, lighting, the actual room geometry)
  stays in QML/`.qml`+asset files, not C++ — C++ is reserved for the
  travel/anchor logic that benefits from it, per the "don't build
  abstractions you don't need" rule.

## 5. Visual continuity with existing retro theme

- Reuse `RetroVHSSucks/assets/` for textures/sprites inside the room
  (posters, CRT monitor decals, VHS tapes as props) rather than sourcing new
  art.
- Port `config/hypr/vhs.frag` (the compositor-wide CRT/VHS post-process
  shader) as a Qt Quick 3D `CustomMaterial`/post-processing `Effect` applied
  to the `View3D`'s output, so the room's render matches the same VHS
  grain/scanline look already used by the lockscreen and screensaver instead
  of looking like a separate, cleaner-rendered layer.

## 6. Build & deploy integration

- **Nix**: add `qt6.qtquick3d` (+ `qt6.qtshadertools` for custom shaders) to
  the runtime closure in `nixos/modules/hyprland.nix` next to `quickshell`;
  add Qt6 dev packages (`qt6.qtbase.dev`, `qt6.qtdeclarative.dev`, `cmake`)
  to the dev shell used to build the `RetroRoom` QML plugin.
- **Kotlin CLI (`imp`)**: no new runtime command needed — Quickshell already
  autostarts via `hl.exec_cmd("qs")` in `autostart.lua`, and the plugin is
  just another QML import Quickshell loads at startup, built like any other
  native QML module (`cmake --build` producing the plugin `.so` +
  `qmldir`, deployed into `config/quickshell/room/plugin/` by whatever
  symlinks `config/quickshell/` today).
- **Tests**: the C++ `CameraRig`/`SceneAnchorRegistry` logic (pure
  transform/state math, no GPU calls) is unit-testable with Qt Test,
  independent of actually rendering — mirrors how `KeybindHelperTest.kt`
  tests `KeybindHelper.kt`'s logic without needing a live Hyprland session.

## 7. Open questions to settle before implementation

1. Exact set of anchors/destinations for v1 (how many bar tiles map to room
   locations, and what each "does something" action actually does once the
   camera arrives).
2. Whether the "notebook closing" animation is a literal lid-closing sprite
   or a more abstract tile-collapse — purely an AGS/GTK-side asset decision,
   independent of this architecture.
3. Idle behavior: does the camera return `home` automatically after N
   seconds, or only on an explicit action (matches the existing
   `hypridle` → screensaver pattern and could reuse the same idle trigger).
