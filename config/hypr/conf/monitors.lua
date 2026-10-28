-- monitors.lua --- display detection and layout
-- License: GPL-3.0-or-later

-- Automatically detect and configure any connected display (laptop screen,
-- external HDMI, DisplayPort, etc.) with its best native resolution and
-- refresh rate, while enforcing integer 1:1 scale (scale = 1) so it never
-- blurs or shrinks the desktop with fractional scaling.
hl.monitor({
    output   = "",
    mode     = "preferred",
    position = "auto",
    scale    = 1,
})
