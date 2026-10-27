#pragma once

// Hand-written C ABI header matching the @CName-exported functions in
// plugin-native/src/nativeMain/kotlin/.../NativeApi.kt 1:1. Kotlin/Native
// also emits its own generated header (libastro_native_api.h) wrapping
// everything behind an opaque API-struct pointer -- this header is used
// instead because every exported function here uses only int32_t/int64_t/
// float, so it's just a normal C shared-library header, no struct/array
// marshalling and no dependency on the generated one.
//
// SpriteKind ordinals (must match SpriteKind.kt exactly):
//   0 SHIP, 1 MOON_0, 2 MOON_1, 3 MOON_2, 4 BULLET, 5 CHAR, 6 EXPLODE, 7 VAX, 8 BACKGROUND
// SoundKind ordinals (must match AstroGlobals.kt's SoundKind exactly):
//   0 SHOOT, 1 EXPLODE

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void astro_init(void);
void astro_set_viewport(float width, float height);
void astro_set_input(int32_t up, int32_t down, int32_t left, int32_t right);
void astro_set_pointer_world(float x, float y);
void astro_set_shooting(int32_t shooting);
void astro_set_spawn_moons(int32_t enabled);
void astro_set_gif_meta(int32_t kind, int32_t frameCount, int32_t delayMillis);

void astro_window_ensure(int64_t id);
void astro_window_remove(int64_t id);
float astro_window_x(int64_t id);
float astro_window_y(int64_t id);
float astro_window_w(int64_t id);
float astro_window_h(int64_t id);
float astro_window_flash_alpha(int64_t id);

void astro_tick(float dt);

float astro_cam_x(void);
float astro_cam_y(void);
float astro_ship_x(void);
float astro_ship_y(void);

int32_t astro_draw_command_count(void);
int32_t astro_draw_kind(int32_t i);
int32_t astro_draw_frame(int32_t i);
float astro_draw_x(int32_t i);
float astro_draw_y(int32_t i);
float astro_draw_scale_x(int32_t i);
float astro_draw_scale_y(int32_t i);
float astro_draw_rot(int32_t i);

int32_t astro_sound_event_count(void);
int32_t astro_sound_event_kind(int32_t i);
void astro_sound_events_clear(void);

int32_t astro_window_close_event_count(void);
int64_t astro_window_close_event_id(int32_t i);
void astro_window_close_events_clear(void);

#ifdef __cplusplus
}
#endif
