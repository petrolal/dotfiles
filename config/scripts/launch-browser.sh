#!/usr/bin/env bash
# launch-browser.sh --- Wayland & GPU-optimized launcher for Google Chrome
# Fixes tearing, tethering, and black YouTube video playback on hybrid Intel+NVIDIA setups.

# Unset legacy/conflicting variables from previous session if still present
unset GBM_BACKEND
unset LIBVA_DRIVER_NAME
unset __GLX_VENDOR_LIBRARY_NAME

# Enforce Ozone Wayland backend
export NIXOS_OZONE_WL=1

exec google-chrome-stable \
  --ozone-platform=wayland \
  --use-gl=angle \
  --use-angle=gl \
  --enable-features=VaapiVideoDecodeLinuxGL,VaapiVideoDecoder,TouchpadOverscrollHistoryNavigation \
  --enable-zero-copy \
  "$@"
