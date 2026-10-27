# Biopunk Operating Desk — Audit & Roadmap

> **Status note:** the scope below (Phase 2's "flat-quad biopunk re-skin,
> keep the orbit grid" decision) was superseded by a later, explicit
> instruction to build a real first-person 3D desk scene and eliminate the
> flat icon grid outright. That's now done -- see `docs/checklist.md`'s
> "Biopunk Operating Desk" section for current, accurate status. The
> analysis below (what existed before this work started) is still correct
> and worth reading for context; just don't take its Phase 2/3/5 *plans* as
> current fact.



Scope note up front: this document plans the Biopunk Operating Desk as an
**extension of the existing `imp overlay` / `imp wallpaper` subsystems**, not
a new standalone project. A from-scratch `biopunk-desk/` Gradle project (as
sketched in the original brief) would duplicate a build, a native-image
pipeline, a NixOS native-lib story, and a GLFW/Wayland integration this repo
has already spent real effort getting right (see `docs/migration_plan.md`).
Several items in the original brief also conflict with decisions already
made and documented here; those conflicts are called out explicitly below
rather than silently overridden.

## Phase 1 — Audit: what's actually here

**Project management.** NixOS Flakes, not plain `configuration.nix`: the
machine config lives in `nixos/` (its own `flake.nix`, `nixosConfigurations`),
and the Java CLI (`imp`) is a *separate* flake at the repo root
(`flake.nix` → `nix/dotfiles-imp.nix`), deliberately decoupled (see that
file's header comment) because "what runs this repo's tooling" and "what
NixOS system this machine boots" are different concerns. There's also
`nixos/templates/{go,python,node,jvm}` — per-language `devShell` templates
used via `direnv`, unrelated to this app.

**Build.** Gradle (`build.gradle`), targeting Java 25, producing a single
GraalVM **native-image** binary (`bin/imp`) via `./gradlew nativeCompile` —
not a plain JAR. This is load-bearing for everything below: any new code
must survive native-image's closed-world static analysis (see the
`reachability-metadata.json` saga in `docs/migration_plan.md` §6).

**Hyprland config.** `config/hypr/hyprland.lua` — Hyprland's **Lua config
API**, not classic `hyprland.conf` syntax. Window rules are
`hl.window_rule({...})` blocks; the `imp-overlay` rule block (float,
fullscreen, no border/shadow/rounding, pin, stay_focused) already exists.
Keybinds follow the ML4W layout; `SUPER+CTRL+O` already launches
`imp overlay` (with the `LD_LIBRARY_PATH` it needs), `SHIFT+SUPER+P` runs
`imp toggle-hud`.

**What already exists and is directly reusable:**

| Biopunk element | Existing code | Gap |
|---|---|---|
| Pulsating Glass Heart | `wallpaper/ShaderPipeline.java` already caches `u_cpu`/`u_ram` uniform locations and sets them every frame from `WallpaperEvents.WallpaperState`; `WallpaperEvents` already samples `/proc/stat`/`/proc/meminfo` on a background virtual thread. | `wallpaper.frag` never *declares* `u_cpu`/`u_ram` — the plumbing is live but nothing reads it. The wallpaper daemon is also still **X11** (`X11Canvas`), not ported to Hyprland/Wayland yet. |
| Petri Dish / scratchpad | — | Nothing exists. Needs design (see Phase 2). |
| Bone Syringe Rack | `overlay/OrbitLayout.java` + `ProcessLauncher.java` — already exactly "N clickable launchers mapped to `ProcessBuilder` execution," just rendered as orbiting app icons instead of syringes. | Needs a fixed (non-orbiting) 5-slot variant and biopunk skin; see Phase 2. |
| Obsidian Scrying Mirror | None. `hyprctl` is not yet invoked from Java anywhere. | New `system.ProcessRunner`-based `hyprctl` client; see Phase 3. |
| Flesh Dial / Valves | None. No PipeWire/WirePlumber (`wpctl`) integration exists. | New rotary-input handling + `wpctl` calls; see Phase 3. |
| Shader hot-reload | `wallpaper/ShaderPipeline.java` tracks `lastMtime` per shader file already — the hot-reload half-exists for the **wallpaper** pipeline. The **overlay**'s `Shader.java`/`QuadRenderer.java` inline GLSL as Java text blocks, with no file-watching at all. | Overlay shaders need to move to loaded files before hot-reload is possible there. |
| GLTF/assimp 3D meshes | None. Every overlay visual today is a flat textured quad (`QuadRenderer`, `IconRenderer`) in a 2D orbital plane viewed through a 3D camera — "3D" currently means "camera has perspective," not "meshes have depth." | See Phase 2's explicit scope decision — this is the single biggest gap vs. the brief. |

**Native library / Wayland environment (already solved, don't redo):**
`NativeLibraryPaths.java` routes LWJGL's own `System.load()` calls to
`/run/opengl-driver/lib`; `nixos/modules/hyprland.nix`'s
`environment.systemPackages` exposes `wayland`/`libxkbcommon` at
`/run/current-system/sw/lib` for GLFW's internal `dlopen()`s; `quick-overlay`
and the `imp overlay` Hyprland keybind both already export that
`LD_LIBRARY_PATH`. A Phase 1 `devShell`/`flake.nix` rewrite, as the original
brief asked for, is **not needed** — it already exists and already works.

**Known blocker that must be weighed into scheduling, not re-discovered
later:** the shipped native-image `imp overlay` binary currently
**segfaults a few hundred ms into `glfwInit()`** on an unattached native
thread (documented in `docs/migration_plan.md` §6, not yet fixed). Every
overlay feature below is only live-verifiable today via `./gradlew
quick-overlay` (plain JVM), not the real binary a user would actually run.
Treat fixing this as a prerequisite, or explicitly accept biopunk work stays
dev-only until it's resolved.

## Phase 2 — Engine architecture: scope decisions

**Wayland layer-shell: explicitly declined, not re-opened.**
`docs/checklist.md` already answers this: *"Decide whether true
`wlr-layer-shell` binding is ever needed — No. `imp overlay` is an
invoked-on-demand modal launcher, not an always-visible panel/dock."* GLFW
itself has no `zwlr_layer_shell_v1` binding (confirmed upstream, LWJGL adds
none either); getting it at all means raw JNI against the Wayland protocol
C headers — a multi-week undertaking with no existing LWJGL/GraalVM
reachability precedent to lean on (same class of problem as the
`liblwjgl.so` reachability-metadata gap in §6, but for a protocol binding
instead of a prebuilt native lib).

Two biopunk elements *do* conceptually want "always visible, bottom layer"
semantics though — the Heart (ambient wallpaper) and (arguably) the Mirror
(workspace status). Recommendation: don't chase layer-shell for these
either. The existing `wallpaper` daemon is already a bottom-layer, always-on
background renderer — just via `hyprpaper`-style ordinary-window
conventions on X11 today, not layer-shell. **Porting `Wallpaper`/`X11Canvas`
to a Wayland backend (still an ordinary toplevel window, Hyprland-ruled to
back-layer behavior the same way the overlay uses window rules for
top-layer behavior) is the real unit of work**, and it's a precondition for
the Heart regardless of layer-shell. The Mirror, Valves, Syringes, and Petri
Dish are all interactive — they belong in the *overlay* (top layer,
invoked-on-demand), not the wallpaper.

**3D meshes via assimp/GLTF: deferred, not adopted for v1.** This is the
biggest mismatch with the brief. Reasons:
- Nothing in this codebase currently depends on `org.lwjgl.assimp`; adding
  it means a new native-image reachability investigation from scratch (see
  how much work the *existing* four LWJGL modules took in
  `docs/migration_plan.md` §6) before any benefit is realized.
  `lwjgl-assimp` is a much larger native lib than `lwjgl-stb`.
- There is currently no 3D mesh asset pipeline, no art for a desk/heart/
  syringe/mirror model, and no mesh renderer (`QuadRenderer`/`IconRenderer`
  are both flat quads with a shared vertex layout).
- The orbital launcher's whole interaction model (ring-based keyboard nav,
  screen-space hit-testing via `OverlayCamera.worldToScreen`) is built
  around flat billboards, not mesh silhouettes.

**Recommendation:** build the biopunk re-skin as an evolution of the
existing flat-quad renderer (shaped/textured quads + GLSL for the organic
look — veins, glass refraction, pulsing emission are *shader* effects, not
geometry) for v1, and revisit assimp/GLTF only if flat-quad biopunk art
direction turns out visually insufficient once something is on screen.
This mirrors how `IconRenderer` already differs from `QuadRenderer` by
*shader*, not by new geometry.

**Scene graph:** no new abstraction needed yet. `OverlayWindow` already
owns a flat list of renderers (`quadRenderer`, `iconRenderer`,
`textRenderer`) each drawn per-frame against shared camera matrices; five
hitboxes plus a heart plus a mirror plane is not enough objects to justify a
graph/node-tree abstraction over what's there. Revisit if the Petri Dish's
drag/drop interactions start needing one.

## Phase 3 — Raycasting & input: what's really needed

The brief's screen-to-world *inverse-matrix* ray cast is overkill for a
scene with no depth variation — every interactive element sits in the same
orbital plane (`z` constant), same as today's app-launcher nodes. The
existing hit-test (`OverlayWindow.nodeAt`, via
`OverlayCamera.worldToScreen` projected the *other* direction, world→screen,
then compared against the mouse in screen space) is simpler, already
live-verified, and should be the pattern reused for every new hitbox —
not replaced with a ray/OBB intersection system that has no current use
case (nothing here has independent depth to disambiguate).

| Hitbox | Action | Mechanism |
|---|---|---|
| `HeartHitbox` | Open a system overview | `ProcessBuilder` launch of a chosen task manager (reuse `ProcessLauncher`) |
| `SyringeHitbox[0..4]` | Launch a mapped app | Reuse `ProcessLauncher` + a **fixed** (non-orbiting) 5-slot layout — a new sibling to `OrbitLayout`, not a repurposing of it, since syringes are a curated short list, not "every installed app" |
| `ValveHitbox` | Volume/brightness | Mouse drag/scroll delta → `wpctl set-volume @DEFAULT_AUDIO_SINK@ N%` via `ProcessRunner` (volume already has prior art: `config/xfce4/scripts/volume.sh`, X11-era, to be superseded) |
| `MirrorHitbox` | Focus a workspace | `hyprctl dispatch workspace <n>` via `ProcessRunner` |
| Petri Dish | Scratch/staging | See below — not a hitbox so much as a drop target |

`Petri Dish` drop-zone: Wayland drag-and-drop into an arbitrary GLFW window
has no supported LWJGL path today (GLFW's own drag-and-drop support is
X11/Win32/Cocoa only — confirmed no Wayland DnD in GLFW 3.4). Recommend
scoping this initially as a **file-staging tray fed by a hotkey** ("stage
clipboard/currently-focused-window's selection into
`~/.cache/imp/petri-dish/`") rather than true OS-level drag-and-drop, and
revisiting real DnD only if GLFW/wlroots gains Wayland support for it.

## Phase 4 — Shader pipeline & telemetry: close the gap that already exists

This is the most done part of the brief, and the plan is mostly "finish
wiring, don't rebuild":

1. **CPU/RAM telemetry already has a reusable, display-agnostic home**:
   `telemetry/SystemVitals.java` (added this pass) — extracted from
   `WallpaperEvents`'s `/proc/stat`/`/proc/meminfo` readers so the overlay
   can sample vitals without depending on the X11-only `wallpaper` package.
   `WallpaperEvents` now delegates to it instead of duplicating the logic.
2. **Heart shader**: write `heart.frag`/`heart.vert` as a *new*, small,
   purpose-built shader pair for a biopunk heart quad (not a 521-line
   monolith like `wallpaper.frag`) — declares `u_time`, `u_cpu`, `u_ram`,
   drives pulse frequency from `u_cpu` and emissive "capillary" scale from
   `u_ram`, matching the brief. Render it with a `HeartRenderer` sibling to
   `QuadRenderer`/`IconRenderer` in the overlay package, fed by a
   `SystemVitals` instance owned by `OverlayWindow`.
3. **Hot-reload**: `ShaderPipeline`'s `lastMtime`-based approach already
   does half of this for the wallpaper; the overlay's `Shader.java` needs
   the same loaded-from-file + `WatchService` treatment *before* `heart.frag`
   benefits from live editing — otherwise every shader tweak needs a
   `quick-overlay` restart, same as the rest of the overlay today.
4. **Post-processing (chromatic aberration, scanlines, vein turbulence)**:
   defer until the heart quad itself renders correctly and is
   live-verified — `wallpaper.frag` already proves this codebase can do
   scanline/glitch/static effects (see its CRT-lock-screen passes), so the
   *technique* isn't new, just needs porting to the overlay's render target.

## Phase 5 — File layout (within this repo, not a new project)

```
src/main/java/dev/petrolal/dotfiles/
├── telemetry/
│   └── SystemVitals.java          # done this pass — CPU/RAM, shared
├── overlay/
│   ├── OverlayWindow.java         # existing; gains heart + syringe rack + mirror + valve wiring
│   ├── HeartRenderer.java         # new — pulsing shader quad, fed by SystemVitals
│   ├── SyringeRack.java           # new — fixed 5-slot launcher layout, ProcessLauncher reuse
│   ├── ScryingMirror.java         # new — hyprctl workspace query/dispatch + preview texture
│   ├── ValveControl.java          # new — drag/scroll → wpctl volume, brightness
│   └── PetriDish.java             # new — hotkey-staged scratch tray (see Phase 3 scope note)
└── system/
    └── ProcessRunner.java         # existing — reused for hyprctl/wpctl calls, no new abstraction needed

src/main/resources/shaders/
├── heart.vert / heart.frag        # new, small, purpose-built (see Phase 4)
└── ...                            # existing wallpaper.* shaders untouched
```

No `build.gradle`/`flake.nix` changes needed for any of the above —
assimp is the only brief item that would require new dependencies, and it's
deferred (Phase 2).

## Suggested build order

1. Fix (or explicitly accept and schedule around) the native-image
   `glfwInit()` segfault — otherwise every feature below only ever runs via
   `quick-overlay`, which was true before this plan and will stay true
   unless addressed.
2. `HeartRenderer` + `heart.frag`/`heart.vert`, fed by `SystemVitals` —
   smallest, most self-contained, and finally pays off the dead `u_cpu`/
   `u_ram` plumbing.
3. `SyringeRack` — directly reuses `ProcessLauncher`; lowest-risk hitbox.
4. `ScryingMirror` and `ValveControl` — both are "shell out via
   `ProcessRunner`," no new rendering concepts.
5. `PetriDish` — the one brief item with no clean existing primitive to
   build on; do last, after the above establishes the interaction pattern.
6. Overlay shader hot-reload (`Shader.java` → file-backed +
   `WatchService`) and post-processing passes — polish, not core loop.

Each step should get the same "live-verify via `quick-overlay`, document
what broke and why" treatment the rest of `docs/migration_plan.md` uses —
that discipline is why the existing overlay code is trustworthy, and there's
no reason to drop it for biopunk-specific additions.
