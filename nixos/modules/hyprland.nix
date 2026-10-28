# Hyprland is now the only session: XFCE/X11/LightDM (desktop.nix) have been
# removed outright (see docs/migration_plan.md section 0A). greetd + tuigreet
# replaces LightDM as a minimal, Wayland-native login manager with no GTK
# theme/greeter dependency.
{ config, lib, pkgs, ... }:

{
  programs.hyprland = {
    enable = true;
    xwayland.enable = true; # some apps are still X11-only and need Xwayland
  };

  services.greetd = {
    enable = true;
    settings.default_session = {
      command = "${pkgs.tuigreet}/bin/tuigreet --time --cmd Hyprland";
      user = "greeter";
    };
  };

  xdg.portal = {
    enable = true;
    extraPortals = [ pkgs.xdg-desktop-portal-hyprland ];
    config.common.default = [ "hyprland" ];
  };

  # PAM service for imp's own GL-rendered screen locker (see
  # src/main/kotlin/dev/petrolal/dotfiles/locker/PamAuthService.kt) -- the
  # empty attrset gets NixOS's standard unix-auth stack, same as every other
  # locker (swaylock, hyprlock) declares for itself.
  security.pam.services.imp-lock = { };

  security.polkit.enable = true;

  # Hyprland has no polkit agent of its own, so run the Hyprland-maintained
  # one for its sessions.
  systemd.user.services.hyprpolkitagent = {
    description = "Hyprland polkit authentication agent";
    wantedBy = [ "graphical-session.target" ];
    partOf = [ "graphical-session.target" ];
    serviceConfig = {
      ExecStart = "${pkgs.hyprpolkitagent}/libexec/hyprpolkitagent";
      Restart = "on-failure";
    };
  };

  # Required for any Wayland compositor.
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

  environment.sessionVariables = lib.mkMerge [
    (lib.mkIf config.dotfiles.nvidia.enable {
      # Works around cursor corruption/invisibility on NVIDIA's wlroots backend.
      WLR_NO_HARDWARE_CURSORS = "1";
      # Auto-detection of the GL/VA-API vendor is unreliable under PRIME
      # offload, so route it to NVIDIA explicitly.
      LIBVA_DRIVER_NAME = "nvidia";
      GBM_BACKEND = "nvidia-drm";
      __GLX_VENDOR_LIBRARY_NAME = "nvidia";
    })
    {
      # AstalNotifd aborts at startup ("Settings schema 'io.astal.notifd'
      # is not installed") unless its compiled GSettings schema is on this
      # path -- confirmed empirically: `ags` is a generic launcher, not
      # built with wrapGAppsHook against astal.notifd specifically, so
      # nothing else wires this up automatically the way a single
      # self-contained GTK app's own nixpkgs derivation would.
      GSETTINGS_SCHEMA_DIR =
        "${pkgs.astal.notifd}/share/gsettings-schemas/${pkgs.astal.notifd.name}/glib-2.0/schemas";
    }
  ];

  environment.systemPackages = with pkgs; [
    ags

    # AstalNotifd/AstalWp/AstalTray/AstalHyprland GI typelibs -- `ags`
    # itself only pulls in the core astal-io/astal-gjs/astal3/astal4 libs
    # it needs to run any Astal app at all; each service library is a
    # separate package and has to be listed explicitly for its
    # `gi://Astal*` import to resolve (see config/ags/widget/Bar.tsx,
    # Notifications.tsx, Osd.tsx).
    astal.notifd
    astal.wireplumber
    astal.tray
    astal.hyprland

    hyprpaper
    hyprlock
    hypridle

    # Astroland ecosystem
    quickshell
    sox
    ffmpeg
    tofi
    rmpc
    mpd
    fyi

    # LWJGL's bundled GLFW native dlopen()s these by soname at runtime (see
    # overlay/NativeLibraryPaths.java and docs/migration_plan.md section 5)
    # -- they're pulled in transitively by Hyprland/Wayland already, but
    # nothing exposes them under a stable, non-hashed path until listed
    # here, which puts them in /run/current-system/sw/lib.
    wayland
    libxkbcommon
  ];
}
