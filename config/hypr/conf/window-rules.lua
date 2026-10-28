-- window-rules.lua --- per-window / per-workspace behavior overrides
-- License: GPL-3.0-or-later

hl.window_rule({
    name = "suppress-fullscreen-events",
    match = { class = ".*" },
    suppress_event = "fullscreen",
})

hl.window_rule({
    name = "suppress-maximize-events",
    match = { class = ".*" },
    suppress_event = "maximize",
})
