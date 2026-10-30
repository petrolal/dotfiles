-- monitors.lua --- display detection and layout
-- License: GPL-3.0-or-later

-- External HDMI monitor: always at the origin (to the left), so it's the
-- stable anchor other outputs are positioned relative to regardless of
-- plug order.
hl.monitor({
    output   = "HDMI-A-1",
    mode     = "preferred",
    position = "0x0",
    scale    = 1,
})

-- Laptop panel: placed immediately to the right of the HDMI monitor
-- (1920x1080 @ scale 1, so position = HDMI monitor's width).
hl.monitor({
    output   = "eDP-1",
    mode     = "preferred",
    position = "1920x0",
    scale    = 1,
})

-- Fallback for anything else plugged in (DisplayPort dock, a different
-- HDMI monitor, etc.) that isn't named above: auto-detect and place.
hl.monitor({
    output   = "",
    mode     = "preferred",
    position = "auto",
    scale    = 1,
})
