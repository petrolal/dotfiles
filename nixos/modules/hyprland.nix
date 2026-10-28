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
      # `start-hyprland` (not the bare `Hyprland` binary) is Hyprland's own
      # uwsm-integration entrypoint: it registers the session as a proper
      # systemd user unit, which is what gives a crashing/closing session a
      # clean handoff back to the greeter instead of leaving the VT in a
      # half-torn-down state. Previously this was "Hyprland", which tuigreet
      # happened to resolve to the same session via its wayland-sessions
      # desktop-file matching -- spelling it out removes that indirection.
      command = "${pkgs.tuigreet}/bin/tuigreet --time --cmd start-hyprland";
      user = "greeter";
    };
  };

  xdg.portal = {
    enable = true;
    extraPortals = [ pkgs.xdg-desktop-portal-hyprland ];
    config.common.default = [ "hyprland" ];
  };


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
    extraPackages = with pkgs; [
      intel-media-driver
      libvdpau-va-gl
    ];
  };

  # NVIDIA modesetting kernel parameters when discrete GPU is enabled
  boot.kernelParams = lib.mkIf config.dotfiles.nvidia.enable [
    "nvidia_drm.modeset=1"
    "nvidia_drm.fbdev=1"
  ];

  environment.sessionVariables = {
    # Tell Chromium/Chrome/Electron to use native Wayland
    NIXOS_OZONE_WL = "1";

    # AstalNotifd aborts at startup ("Settings schema 'io.astal.notifd'
    # is not installed") unless its compiled GSettings schema is on this
    # path -- confirmed empirically: `ags` is a generic launcher, not
    # built with wrapGAppsHook against astal.notifd specifically, so
    # nothing else wires this up automatically the way a single
    # self-contained GTK app's own nixpkgs derivation would.
    GSETTINGS_SCHEMA_DIR =
      "${pkgs.astal.notifd}/share/gsettings-schemas/${pkgs.astal.notifd.name}/glib-2.0/schemas";
  };

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
    libnotify

    # Wayland runtime/client libraries
    wayland
    libxkbcommon
  ];
}
