-- cursor.lua --- hardware cursor behavior
-- License: GPL-3.0-or-later

-- "Auto" (2): Hyprland's isNvidia() && isMgpu() check is session-wide, not
-- per-monitor, so on this hybrid Intel+NVIDIA laptop it disables hardware
-- cursors on BOTH eDP-1 and HDMI-A-1 -- there is no Hyprland setting to
-- force hardware cursors on just the Intel panel while using software on
-- the NVIDIA-driven HDMI-A-1 output. Forcing hardware cursors everywhere
-- (previously `false`) broke the NVIDIA output specifically: its GL cursor
-- blit fails every frame (`EGL (blit): glCheckFramebufferStatus failed:
-- 1282` / `drm: Backend requires blit, but cursor blit failed` spam in the
-- Hyprland log), which is what caused the glitches/visual bugs on HDMI.
-- Trade-off: the laptop's 1000Hz mouse loses hardware-cursor tear-freeness
-- too, in exchange for a non-glitchy external monitor.
hl.config({
    cursor = {
        no_hardware_cursors = 2,
    },
})
