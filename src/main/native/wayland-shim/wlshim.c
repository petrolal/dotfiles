/* wlshim.c --- Thin C bridge between libwayland-client and the Java FFM renderer.
 *
 * Only the parts of the Wayland protocol that *must* be marshaled through
 * libwayland's variadic wl_proxy_marshal_flags() live here (registry binding,
 * zwlr_layer_shell_v1 surface setup, seat/pointer listeners). Everything that
 * is a plain, fixed-signature C ABI call -- EGL context creation, GL calls --
 * is done directly from Java via java.lang.foreign against libEGL.so/libGL.so,
 * the same way the existing X11Canvas talks to libGL.so via GLX.
 *
 * License: GPL-3.0-or-later
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wayland-client.h>
#include <wayland-egl.h>

#include "generated/wlr-layer-shell-unstable-v1-client-protocol.h"

#define WLSHIM_MAX_OUTPUTS 8
#define WLSHIM_MAX_SURFACES 8

typedef struct {
    struct wl_output *output;
    int32_t width;
    int32_t height;
} wlshim_output_info;

typedef struct {
    struct wl_surface *wl_surface;
    struct zwlr_layer_surface_v1 *layer_surface;
    struct wl_egl_window *egl_window;
    int32_t width;
    int32_t height;
    int configured;
} wlshim_surface;

struct wlshim_ctx {
    struct wl_display *display;
    struct wl_registry *registry;
    struct wl_compositor *compositor;
    struct zwlr_layer_shell_v1 *layer_shell;
    struct wl_seat *seat;
    struct wl_pointer *pointer;

    wlshim_output_info outputs[WLSHIM_MAX_OUTPUTS];
    int output_count;

    wlshim_surface surfaces[WLSHIM_MAX_SURFACES];
    int surface_count;

    /* Last known pointer state, tracked against whichever wl_surface it is
     * currently over -- background-layer surfaces only receive pointer
     * events for the region not covered by a window above them, so "which
     * surface" doubles as "which monitor". */
    struct wl_surface *pointer_over;
    float pointer_x;
    float pointer_y;
    int pointer_left;
    int pointer_right;
};

/* ---- wl_output listener: only used to learn each output's pixel size ---- */

static void output_mode(void *data, struct wl_output *wl_output, uint32_t flags,
                         int32_t width, int32_t height, int32_t refresh) {
    struct wlshim_ctx *ctx = data;
    for (int i = 0; i < ctx->output_count; i++) {
        if (ctx->outputs[i].output == wl_output && (flags & WL_OUTPUT_MODE_CURRENT)) {
            ctx->outputs[i].width = width;
            ctx->outputs[i].height = height;
        }
    }
}
static void output_geometry(void *data, struct wl_output *wl_output, int32_t x, int32_t y,
                             int32_t pw, int32_t ph, int32_t subpixel, const char *make,
                             const char *model, int32_t transform) {}
static void output_done(void *data, struct wl_output *wl_output) {}
static void output_scale(void *data, struct wl_output *wl_output, int32_t factor) {}
static void output_name(void *data, struct wl_output *wl_output, const char *name) {}
static void output_description(void *data, struct wl_output *wl_output, const char *description) {}

static const struct wl_output_listener output_listener = {
    .geometry = output_geometry,
    .mode = output_mode,
    .done = output_done,
    .scale = output_scale,
    .name = output_name,
    .description = output_description,
};

/* ---- wl_pointer listener: tracks cursor position/buttons per-surface ---- */

static void pointer_enter(void *data, struct wl_pointer *wl_pointer, uint32_t serial,
                           struct wl_surface *surface, wl_fixed_t sx, wl_fixed_t sy) {
    struct wlshim_ctx *ctx = data;
    ctx->pointer_over = surface;
    ctx->pointer_x = wl_fixed_to_double(sx);
    ctx->pointer_y = wl_fixed_to_double(sy);
}
static void pointer_leave(void *data, struct wl_pointer *wl_pointer, uint32_t serial,
                           struct wl_surface *surface) {
    struct wlshim_ctx *ctx = data;
    if (ctx->pointer_over == surface) {
        ctx->pointer_over = NULL;
    }
}
static void pointer_motion(void *data, struct wl_pointer *wl_pointer, uint32_t time,
                            wl_fixed_t sx, wl_fixed_t sy) {
    struct wlshim_ctx *ctx = data;
    ctx->pointer_x = wl_fixed_to_double(sx);
    ctx->pointer_y = wl_fixed_to_double(sy);
}
static void pointer_button(void *data, struct wl_pointer *wl_pointer, uint32_t serial,
                            uint32_t time, uint32_t button, uint32_t state) {
    struct wlshim_ctx *ctx = data;
    int pressed = (state == WL_POINTER_BUTTON_STATE_PRESSED);
    /* BTN_LEFT = 0x110, BTN_RIGHT = 0x111 (linux/input-event-codes.h) */
    if (button == 0x110) ctx->pointer_left = pressed;
    if (button == 0x111) ctx->pointer_right = pressed;
}
static void pointer_axis(void *data, struct wl_pointer *wl_pointer, uint32_t time,
                          uint32_t axis, wl_fixed_t value) {}
static void pointer_frame(void *data, struct wl_pointer *wl_pointer) {}
static void pointer_axis_source(void *data, struct wl_pointer *wl_pointer, uint32_t axis_source) {}
static void pointer_axis_stop(void *data, struct wl_pointer *wl_pointer, uint32_t time, uint32_t axis) {}
static void pointer_axis_discrete(void *data, struct wl_pointer *wl_pointer, uint32_t axis, int32_t discrete) {}

static const struct wl_pointer_listener pointer_listener = {
    .enter = pointer_enter,
    .leave = pointer_leave,
    .motion = pointer_motion,
    .button = pointer_button,
    .axis = pointer_axis,
    .frame = pointer_frame,
    .axis_source = pointer_axis_source,
    .axis_stop = pointer_axis_stop,
    .axis_discrete = pointer_axis_discrete,
};

/* ---- wl_seat listener: grab the pointer capability once available ---- */

static void seat_capabilities(void *data, struct wl_seat *seat, uint32_t caps) {
    struct wlshim_ctx *ctx = data;
    if ((caps & WL_SEAT_CAPABILITY_POINTER) && ctx->pointer == NULL) {
        ctx->pointer = wl_seat_get_pointer(seat);
        wl_pointer_add_listener(ctx->pointer, &pointer_listener, ctx);
    }
}
static void seat_name(void *data, struct wl_seat *seat, const char *name) {}

static const struct wl_seat_listener seat_listener = {
    .capabilities = seat_capabilities,
    .name = seat_name,
};

/* ---- wl_registry listener: bind the globals we need ---- */

static void registry_global(void *data, struct wl_registry *registry, uint32_t name,
                             const char *interface, uint32_t version) {
    struct wlshim_ctx *ctx = data;
    if (strcmp(interface, wl_compositor_interface.name) == 0) {
        ctx->compositor = wl_registry_bind(registry, name, &wl_compositor_interface, 4);
    } else if (strcmp(interface, zwlr_layer_shell_v1_interface.name) == 0) {
        ctx->layer_shell = wl_registry_bind(registry, name, &zwlr_layer_shell_v1_interface, 1);
    } else if (strcmp(interface, wl_seat_interface.name) == 0) {
        ctx->seat = wl_registry_bind(registry, name, &wl_seat_interface, 7);
        wl_seat_add_listener(ctx->seat, &seat_listener, ctx);
    } else if (strcmp(interface, wl_output_interface.name) == 0 && ctx->output_count < WLSHIM_MAX_OUTPUTS) {
        struct wl_output *output = wl_registry_bind(registry, name, &wl_output_interface, 2);
        wl_output_add_listener(output, &output_listener, ctx);
        ctx->outputs[ctx->output_count].output = output;
        ctx->outputs[ctx->output_count].width = 0;
        ctx->outputs[ctx->output_count].height = 0;
        ctx->output_count++;
    }
}
static void registry_global_remove(void *data, struct wl_registry *registry, uint32_t name) {}

static const struct wl_registry_listener registry_listener = {
    .global = registry_global,
    .global_remove = registry_global_remove,
};

/* ---- layer_surface listener: compositor tells us our configured size ---- */

static void layer_surface_configure(void *data, struct zwlr_layer_surface_v1 *surface,
                                     uint32_t serial, uint32_t width, uint32_t height) {
    wlshim_surface *s = data;
    s->width = (int32_t) width;
    s->height = (int32_t) height;
    s->configured = 1;
    zwlr_layer_surface_v1_ack_configure(surface, serial);
    if (s->egl_window != NULL) {
        wl_egl_window_resize(s->egl_window, (int) width, (int) height, 0, 0);
    }
}
static void layer_surface_closed(void *data, struct zwlr_layer_surface_v1 *surface) {
    wlshim_surface *s = data;
    s->configured = 0;
}

static const struct zwlr_layer_surface_v1_listener layer_surface_listener = {
    .configure = layer_surface_configure,
    .closed = layer_surface_closed,
};

/* ---------------------------------------------------------------------- */
/* Public API, called from Java via java.lang.foreign.                    */
/* ---------------------------------------------------------------------- */

struct wlshim_ctx *wlshim_connect(void) {
    struct wlshim_ctx *ctx = calloc(1, sizeof(struct wlshim_ctx));
    if (ctx == NULL) return NULL;

    ctx->display = wl_display_connect(NULL);
    if (ctx->display == NULL) {
        free(ctx);
        return NULL;
    }

    ctx->registry = wl_display_get_registry(ctx->display);
    wl_registry_add_listener(ctx->registry, &registry_listener, ctx);

    /* Two roundtrips: one to receive all `global` events, one more so every
     * bound global (wl_seat, wl_output, ...) has delivered its own initial
     * events (seat capabilities, output mode) before we return. */
    wl_display_roundtrip(ctx->display);
    wl_display_roundtrip(ctx->display);

    if (ctx->compositor == NULL || ctx->layer_shell == NULL) {
        fprintf(stderr, "wlshim: compositor does not expose wl_compositor + "
                         "zwlr_layer_shell_v1 (not a wlroots-based compositor?)\n");
        wl_display_disconnect(ctx->display);
        free(ctx);
        return NULL;
    }
    return ctx;
}

int wlshim_output_count(struct wlshim_ctx *ctx) {
    return ctx->output_count;
}

void *wlshim_get_output(struct wlshim_ctx *ctx, int index) {
    if (index < 0 || index >= ctx->output_count) return NULL;
    return ctx->outputs[index].output;
}

void wlshim_get_output_size(struct wlshim_ctx *ctx, int index, int *out_w, int *out_h) {
    if (index < 0 || index >= ctx->output_count) {
        *out_w = 0;
        *out_h = 0;
        return;
    }
    *out_w = ctx->outputs[index].width;
    *out_h = ctx->outputs[index].height;
}

/* Creates one background-layer surface pinned to `output` (NULL = compositor
 * picks), sized to fill it, and waits for the first configure. Returns a
 * surface handle, or NULL on failure. The wl_display* and wl_egl_window*
 * needed for EGL are written to the out-params so Java can drive EGL itself. */
void *wlshim_create_background_surface(struct wlshim_ctx *ctx, void *output,
                                        void **out_wl_display, void **out_wl_egl_window) {
    if (ctx->surface_count >= WLSHIM_MAX_SURFACES) return NULL;
    wlshim_surface *s = &ctx->surfaces[ctx->surface_count];

    s->wl_surface = wl_compositor_create_surface(ctx->compositor);
    s->layer_surface = zwlr_layer_shell_v1_get_layer_surface(
        ctx->layer_shell, s->wl_surface, (struct wl_output *) output,
        ZWLR_LAYER_SHELL_V1_LAYER_BACKGROUND, "dotfiles-wallpaper");

    zwlr_layer_surface_v1_set_size(s->layer_surface, 0, 0); /* 0,0 = fill the output */
    zwlr_layer_surface_v1_set_anchor(s->layer_surface,
        ZWLR_LAYER_SURFACE_V1_ANCHOR_TOP | ZWLR_LAYER_SURFACE_V1_ANCHOR_BOTTOM |
        ZWLR_LAYER_SURFACE_V1_ANCHOR_LEFT | ZWLR_LAYER_SURFACE_V1_ANCHOR_RIGHT);
    zwlr_layer_surface_v1_set_exclusive_zone(s->layer_surface, -1);
    /* No keyboard focus: this is a desktop background, not an app window.
     * Pointer events still arrive for the screen area no other surface covers. */
    zwlr_layer_surface_v1_set_keyboard_interactivity(s->layer_surface,
        ZWLR_LAYER_SURFACE_V1_KEYBOARD_INTERACTIVITY_NONE);

    zwlr_layer_surface_v1_add_listener(s->layer_surface, &layer_surface_listener, s);
    wl_surface_commit(s->wl_surface);

    s->configured = 0;
    while (!s->configured) {
        if (wl_display_dispatch(ctx->display) < 0) {
            return NULL;
        }
    }

    s->egl_window = wl_egl_window_create(s->wl_surface, s->width, s->height);
    ctx->surface_count++;

    *out_wl_display = ctx->display;
    *out_wl_egl_window = s->egl_window;
    return s->wl_surface;
}

int wlshim_surface_width(struct wlshim_ctx *ctx, void *wl_surface) {
    for (int i = 0; i < ctx->surface_count; i++) {
        if (ctx->surfaces[i].wl_surface == wl_surface) return ctx->surfaces[i].width;
    }
    return 0;
}
int wlshim_surface_height(struct wlshim_ctx *ctx, void *wl_surface) {
    for (int i = 0; i < ctx->surface_count; i++) {
        if (ctx->surfaces[i].wl_surface == wl_surface) return ctx->surfaces[i].height;
    }
    return 0;
}

/* Non-blocking: pumps already-buffered events and flushes outgoing requests.
 * Must be called once per render frame from the Java main loop. */
int wlshim_dispatch(struct wlshim_ctx *ctx) {
    wl_display_dispatch_pending(ctx->display);
    return wl_display_flush(ctx->display);
}

/* Writes the last-known pointer state and whether it is currently over
 * `wl_surface`. `over` is 0/1. */
void wlshim_get_pointer(struct wlshim_ctx *ctx, void *wl_surface, float *x, float *y,
                         int *left, int *right, int *over) {
    *x = ctx->pointer_x;
    *y = ctx->pointer_y;
    *left = ctx->pointer_left;
    *right = ctx->pointer_right;
    *over = (ctx->pointer_over == wl_surface) ? 1 : 0;
}

void wlshim_destroy(struct wlshim_ctx *ctx) {
    if (ctx == NULL) return;
    for (int i = 0; i < ctx->surface_count; i++) {
        if (ctx->surfaces[i].egl_window) wl_egl_window_destroy(ctx->surfaces[i].egl_window);
        if (ctx->surfaces[i].layer_surface) zwlr_layer_surface_v1_destroy(ctx->surfaces[i].layer_surface);
        if (ctx->surfaces[i].wl_surface) wl_surface_destroy(ctx->surfaces[i].wl_surface);
    }
    if (ctx->display) wl_display_disconnect(ctx->display);
    free(ctx);
}
