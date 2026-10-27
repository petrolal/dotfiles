@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package dev.petrolal.dotfiles.astronative

/**
 * The plain C ABI this engine exposes to `native-plugin/` (the actual
 * Hyprland-facing `.so`, plain C++ -- see its CMakeLists.txt). Every
 * exported function uses only `Int`/`Long`/`Float` so it's callable as an
 * ordinary C symbol via `@CName`, with no struct/array marshalling and no
 * dependency on Kotlin/Native's generated API-struct header. All state is
 * single-threaded: the shim only ever calls these from Hyprland's own
 * render/event-loop thread, same as the original plugin's assumptions.
 */

private var lastDrawCommands: List<DrawCommand> = emptyList()

@CName("astro_init")
fun astroInit() {
    AstroEngine.ensureStarted()
}

@CName("astro_set_viewport")
fun astroSetViewport(width: Float, height: Float) {
    AstroGlobals.viewportWidth = width
    AstroGlobals.viewportHeight = height
}

@CName("astro_set_input")
fun astroSetInput(up: Int, down: Int, left: Int, right: Int) {
    AstroGlobals.inputVec = Vec2(
        (if (right != 0) 1f else 0f) - (if (left != 0) 1f else 0f),
        (if (down != 0) 1f else 0f) - (if (up != 0) 1f else 0f),
    )
}

@CName("astro_set_pointer_world")
fun astroSetPointerWorld(x: Float, y: Float) {
    AstroGlobals.pointerWorldPos = Vec2(x, y)
}

@CName("astro_set_shooting")
fun astroSetShooting(shooting: Int) {
    AstroGlobals.ship.isShooting = shooting != 0
}

@CName("astro_set_spawn_moons")
fun astroSetSpawnMoons(enabled: Int) {
    AstroGlobals.spawnMoonsEnabled = enabled != 0
}

/** Called once per [SpriteKind] at startup, after the shim decodes that GIF via `gifdec` -- see `NativeAnimatedSprite`'s class doc. */
@CName("astro_set_gif_meta")
fun astroSetGifMeta(kind: Int, frameCount: Int, delayMillis: Int) {
    val spriteKind = SpriteKind.entries.getOrNull(kind) ?: return
    AstroGlobals.gifMeta[spriteKind] = GifMeta(frameCount.coerceAtLeast(1), (delayMillis / 1000f).coerceAtLeast(0.001f))
}

@CName("astro_window_ensure")
fun astroWindowEnsure(id: Long) {
    if (!AstroGlobals.windows.containsKey(id)) {
        val window = AstroGlobals.scene.instantiate(WindowBody(id))
        AstroGlobals.windows[id] = window
    }
}

@CName("astro_window_remove")
fun astroWindowRemove(id: Long) {
    AstroGlobals.windows.remove(id)?.dispose()
}

@CName("astro_window_x")
fun astroWindowX(id: Long): Float = AstroGlobals.windows[id]?.getWorldPos()?.x ?: 0f

@CName("astro_window_y")
fun astroWindowY(id: Long): Float = AstroGlobals.windows[id]?.getWorldPos()?.y ?: 0f

@CName("astro_window_w")
fun astroWindowW(id: Long): Float = AstroGlobals.windows[id]?.size?.x ?: 300f

@CName("astro_window_h")
fun astroWindowH(id: Long): Float = AstroGlobals.windows[id]?.size?.y ?: 300f

@CName("astro_window_flash_alpha")
fun astroWindowFlashAlpha(id: Long): Float = AstroGlobals.windows[id]?.flashAlpha ?: 0f

@CName("astro_tick")
fun astroTick(dt: Float) {
    AstroEngine.tick(dt)
    lastDrawCommands = AstroEngine.snapshotDrawCommands()
}

@CName("astro_cam_x")
fun astroCamX(): Float = AstroGlobals.camPos.x

@CName("astro_cam_y")
fun astroCamY(): Float = AstroGlobals.camPos.y

@CName("astro_ship_x")
fun astroShipX(): Float = AstroGlobals.ship.getWorldPos().x

@CName("astro_ship_y")
fun astroShipY(): Float = AstroGlobals.ship.getWorldPos().y

@CName("astro_draw_command_count")
fun astroDrawCommandCount(): Int = lastDrawCommands.size

@CName("astro_draw_kind")
fun astroDrawKind(i: Int): Int = lastDrawCommands.getOrNull(i)?.kind?.ordinal ?: -1

@CName("astro_draw_frame")
fun astroDrawFrame(i: Int): Int = lastDrawCommands.getOrNull(i)?.frame ?: 0

@CName("astro_draw_x")
fun astroDrawX(i: Int): Float = lastDrawCommands.getOrNull(i)?.pos?.x ?: 0f

@CName("astro_draw_y")
fun astroDrawY(i: Int): Float = lastDrawCommands.getOrNull(i)?.pos?.y ?: 0f

@CName("astro_draw_scale_x")
fun astroDrawScaleX(i: Int): Float = lastDrawCommands.getOrNull(i)?.scale?.x ?: 1f

@CName("astro_draw_scale_y")
fun astroDrawScaleY(i: Int): Float = lastDrawCommands.getOrNull(i)?.scale?.y ?: 1f

@CName("astro_draw_rot")
fun astroDrawRot(i: Int): Float = lastDrawCommands.getOrNull(i)?.rot ?: 0f

@CName("astro_sound_event_count")
fun astroSoundEventCount(): Int = AstroGlobals.soundEvents.size

@CName("astro_sound_event_kind")
fun astroSoundEventKind(i: Int): Int = AstroGlobals.soundEvents.getOrNull(i)?.ordinal ?: -1

@CName("astro_sound_events_clear")
fun astroSoundEventsClear() {
    AstroGlobals.soundEvents.clear()
}

@CName("astro_window_close_event_count")
fun astroWindowCloseEventCount(): Int = AstroGlobals.windowCloseEvents.size

@CName("astro_window_close_event_id")
fun astroWindowCloseEventId(i: Int): Long = AstroGlobals.windowCloseEvents.getOrNull(i) ?: 0L

@CName("astro_window_close_events_clear")
fun astroWindowCloseEventsClear() {
    AstroGlobals.windowCloseEvents.clear()
}
