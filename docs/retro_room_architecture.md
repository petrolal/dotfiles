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
 ├─ ags run apps/ags    (AGS/Astal bar, GTK)     ← top layer, unchanged process
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

**AGS bar tile click handler (`apps/ags/widget/Bar.tsx` or wherever the
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

Modular directory: `plugins/retroroom/` (QML module name `RetroRoom 1.0`):

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

- Reuse `assets/` and `assets/room/` for textures and props inside the room
  (band posters: Deftones/Korn/Slipknot/Linkin Park, CRT PC screens, PS1 console,
  alarm clock, guitar, skateboard, wood furniture, VHS tapes) ensuring full visual
  continuity with authentic 90s bedroom aesthetics.
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
- **Kotlin CLI (`imp`)**: Integrated via `imp room [--action travel|reset|status|vhs]`
  and `dev.petrolal.dotfiles.desktop.RetroRoomControl`. The plugin is
  loaded by Quickshell at startup via `QML2_IMPORT_PATH` set in `env.lua`
  pointing to `build/plugins/retroroom`.
- **Tests**: the C++ `CameraRig`/`SceneAnchorRegistry` logic (pure
  transform/state math, no GPU calls) is unit-testable with Qt Test,
  independent of actually rendering — automated via `./gradlew testRetroRoom`.

## 7. Settled Decisions

1. **Anchors for v1**:
   - `"home"`: Room overview (wide camera angle).
   - `"terminal"`: CRT monitor on the desk.
   - `"music"`: Retro boombox / stereo cassette deck.
2. **Notebook Closing Animation**:
   - Pure GTK/CSS 3D perspective fold that tilts and folds the bar downward like a closing laptop lid before triggering camera travel.
3. **Idle Behavior**:
   - Timed inactivity return: A 30-second inactivity timer automatically returns the camera to `"home"` and unfolds the bar.

## 8. Implementation Status & Progress

- [x] **Step 1: NixOS Packages & Environment**
  - Added `qt6.qtquick3d` and `qt6.qtshadertools` to `environment.systemPackages` in `nixos/modules/hyprland.nix`.
  - Configured `QML2_IMPORT_PATH` in `environment.sessionVariables` pointing to `lib/qt-6/qml` for Quickshell and QtQuick3D.
  - Added `devShells.room` to root `flake.nix` with CMake, Ninja, and Qt6 development libraries.
- [x] **Step 2: C++ QML Plugin (`RetroRoom 1.0`)**
  - Implemented in `plugins/retroroom/`:
    - `SceneAnchorRegistry`: Maps `"home"`, `"terminal"`, and `"music"` strings to 3D nodes.
    - `CameraRig`: Manages smooth camera interpolation with shortest-path angular math.
    - `RoomSceneController`: Exposes `travelTo(anchorId)`, `transitioning`, and `currentAnchor` to QML with busy rejection.
  - CMake setup using `qt_add_qml_module`.
  - Automated Qt unit tests (`tests/test_retroroom.cpp`) passing 100%.
  - Added `build.sh` build script and Gradle integration (`./gradlew compileRetroRoom`, `./gradlew testRetroRoom`).
- [x] **Step 3: 3D Scene & Quickshell Integration**
  - Created `apps/quickshell/room/RoomScene.qml`:
    - Full `View3D` layer surface with `SceneEnvironment` (MSAA antialiasing, dark retro room palette).
    - 3D Room geometry: floor, back & side walls, industrial workbench/desk.
    - 3D CRT Computer workstation at `"terminal"` anchor with authentic 90s chassis, keyboard, warning label, and phosphor glow point light.
    - Retro Boombox & Cassette Stereo at `"music"` anchor with textured faceplate (`radio.png`), spinning cassette/disc accent (`dvd.png`), and warm amber LED glow.
    - Masterful retro logo poster (`m_for_masterful.png`) on the back wall.
    - Connected `CameraRig`, `SceneAnchorRegistry`, and `RoomSceneController` with 400ms cubic travel curves.
  - Mounted in `apps/quickshell/shell.qml`:
    - `PanelWindow` on primary display with `WlrLayershell.layer: WlrLayer.Background`, `exclusionMode: ExclusionMode.Ignore`, and click passthrough `mask: Region {}`.
    - `IpcHandler { target: "room" }` with `travel(anchorId)`, `reset()`, and `status()` endpoints.
- [x] **Step 4: AGS Bar Integration & 30s Auto-Reset**
  - Created `apps/ags/widget/RoomManager.ts`:
    - Manages `isFolded` state and 30-second inactivity timer.
    - Folds the top bar down like a laptop lid before signaling camera travel.
    - Automatically restores camera to `"home"` and unfolds bar after 30s of inactivity.
  - Updated `apps/ags/widget/Bar.tsx`:
    - Bound `roomManager.isFolded` to `centerbox.folded`.
    - Made `"📼 SHUT THE FUCK UP!"` logo button trigger `roomManager.reset()`.
  - Updated `apps/ags/widget/Workspaces.tsx` and `apps/ags/widget/Media.tsx`:
    - Clicking `"TAPE:"` triggers travel to `"terminal"`.
    - Clicking cassette VFD / HUD button triggers travel to `"music"`.
  - Updated `apps/ags/style.scss`:
    - Added 3D perspective fold transition: `transform: perspective(600px) rotateX(-85deg) scaleY(0.1)` with 250ms duration.
    - Kept 100% of the exact existing visual style of the bar intact when unfolded.
- [x] **Step 5: Snappy Window Tiling & Animations**
  - Added tuned `animations` block to `config/hypr/conf/input.lua`:
    - Custom snappy bezier curve: `"0.05, 0.9, 0.1, 1.05"`.
    - Window spawn/popin speed factor `2` (~150ms `popin 90%`) eliminating tile lag on `<meta> + RETURN`.
- [x] **Step 6: VHS Post-Processing Effect for 3D Room**
  - Created `apps/quickshell/room/vhs_effect.frag` and `VhsEffect.qml` porting CRT curvature, scanlines, chromatic aberration, and tube vignette.
  - Attached to `SceneEnvironment.effects` in `RoomScene.qml`.
  - Added dynamic toggling via `qs ipc call room toggleVhs` and `imp room --action vhs`.

## 9. Ergonomics & Performance: Fast Tile Openings & Snappy Transitions

For comfortable daily use, animations must not feel sluggish or introduce friction when launching windows or navigating points of interest:

### 1. Hyprland Window Tiling (`SUPER + RETURN` / Kitty)
- **Implemented**: Tuned `animations` block in `config/hypr/conf/input.lua` using `snappy = "0.05, 0.9, 0.1, 1.05"` with speed factor 2 (~150ms `popin 90%`). Spawning terminals or tiling windows is virtually instantaneous.

### 2. Room Camera Travel Speed
- **Implemented**: Camera travel duration in `CameraRig` is set to **400ms** with `QEasingCurve::InOutCubic` and shortest-path spherical angular math.

### 3. AGS Bar Folding Transition Speed
- **Implemented**: The GTK/CSS perspective fold transition in `apps/ags/style.scss` uses a crisp **250ms** duration.

### 4. Zero Interruption Principle
- Camera dolly and 3D rendering take place entirely on the background layer-shell surface in Quickshell (`qs`). Keystrokes, terminal spawns (`SUPER + RETURN`), application launchers, and window management are handled directly by Hyprland without waiting on any 3D transition.


