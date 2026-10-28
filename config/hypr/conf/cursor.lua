-- cursor.lua --- hardware cursor behavior
-- License: GPL-3.0-or-later

-- Explicitly enable hardware cursors on all displays (including external NVIDIA
-- HDMI). Prevents software cursor composition and page-flip bottlenecks at high
-- polling rates (e.g. 1000Hz gaming mice) that cause tearing when moving the mouse.
hl.config({
    cursor = {
        no_hardware_cursors = false,
    },
})
