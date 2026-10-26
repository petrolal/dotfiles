# Additive: enables Hyprland as a session choice alongside the existing
# XFCE/X11 setup in desktop.nix. LightDM lists both xsessions and
# wayland-sessions entries (programs.hyprland.enable adds the latter
# automatically), so XFCE stays selectable as a fallback while Hyprland and
# the overlay launcher get proven out on this NVIDIA PRIME hybrid-graphics
# host. See docs/migration_plan.md for the decommission step that removes
# desktop.nix's X11/XFCE/LightDM block once Hyprland is confirmed stable.
{ config, lib, pkgs, ... }:

{
  programs.hyprland = {
    enable = true;
    xwayland.enable = true; # most XFCE-era apps, and the overlay's dev/testing on X11, still need this
  };

  xdg.portal = {
    enable = true;
    extraPortals = [ pkgs.xdg-desktop-portal-hyprland ];
    config.common.default = [ "hyprland" ];
  };

  security.polkit.enable = true;

  # XFCE ships its own polkit agent; Hyprland doesn't, so run the
  # Hyprland-maintained one for its sessions specifically.
  systemd.user.services.hyprpolkitagent = {
    description = "Hyprland polkit authentication agent";
    wantedBy = [ "graphical-session.target" ];
    partOf = [ "graphical-session.target" ];
    serviceConfig = {
      ExecStart = "${pkgs.hyprpolkitagent}/libexec/hyprpolkitagent";
      Restart = "on-failure";
    };
  };

  # Required for any Wayland compositor; desktop.nix's X11/XFCE stack
  # doesn't turn this on today.
  hardware.graphics = {
    enable = true;
    enable32Bit = true;
  };

  # NVIDIA + wlroots rough edges -- only takes effect on hosts where
  # dotfiles.nvidia.enable is set (see modules/nvidia.nix, host "primary").
  boot.kernelParams = lib.mkIf config.dotfiles.nvidia.enable [
    "nvidia_drm.modeset=1"
    "nvidia_drm.fbdev=1"
  ];

  environment.sessionVariables = lib.mkIf config.dotfiles.nvidia.enable {
    # Works around cursor corruption/invisibility on NVIDIA's wlroots backend.
    WLR_NO_HARDWARE_CURSORS = "1";
    # Auto-detection of the GL/VA-API vendor is unreliable under PRIME
    # offload, so route it to NVIDIA explicitly.
    LIBVA_DRIVER_NAME = "nvidia";
    GBM_BACKEND = "nvidia-drm";
    __GLX_VENDOR_LIBRARY_NAME = "nvidia";
  };

  environment.systemPackages = with pkgs; [
    waybar
    hyprpaper
    hyprlock
    hypridle
  ];
}
