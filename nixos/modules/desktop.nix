{ config, pkgs, ... }:

{
  # Time & Locale
  time.timeZone = "America/Sao_Paulo";
  i18n.defaultLocale = "en_US.UTF-8";
  i18n.extraLocaleSettings = {
    LC_ADDRESS = "pt_BR.UTF-8";
    LC_IDENTIFICATION = "pt_BR.UTF-8";
    LC_MEASUREMENT = "pt_BR.UTF-8";
    LC_MONETARY = "pt_BR.UTF-8";
    LC_NAME = "pt_BR.UTF-8";
    LC_NUMERIC = "pt_BR.UTF-8";
    LC_PAPER = "pt_BR.UTF-8";
    LC_TELEPHONE = "pt_BR.UTF-8";
    LC_TIME = "pt_BR.UTF-8";
  };

  # Pure Wayland session: X11 server, XFCE, lightdm, and picom are gone.
  # greetd (text greeter) -> Wayfire (wlroots compositor) is the only session.
  services.xserver.enable = false;

  services.greetd = {
    enable = true;
    settings = {
      default_session = {
        command = "${pkgs.tuigreet}/bin/tuigreet --time --remember --cmd wayfire";
        user = "greeter";
      };
    };
  };

  programs.wayfire = {
    enable = true;
    xwayland.enable = true; # keep Xwayland for legacy X11-only apps, not the host session
  };

  # xkb now lives at the compositor/console layer, not an X server.
  console.keyMap = "us-acentos";
  environment.sessionVariables.XKB_DEFAULT_LAYOUT = "us";
  environment.sessionVariables.XKB_DEFAULT_VARIANT = "intl";

  # Pipewire Sound
  services.printing.enable = true;
  services.pulseaudio.enable = false;
  security.rtkit.enable = true;
  services.pipewire = {
    enable = true;
    alsa.enable = true;
    alsa.support32Bit = true;
    pulse.enable = true;
  };

  # Global Theme & Toolkit Overrides
  environment.variables = {
    GTK_THEME = "Adwaita:dark";
    QT_STYLE_OVERRIDE = "Adwaita-Dark";
  };

  environment.systemPackages = with pkgs; [
    adwaita-icon-theme
    gnome-themes-extra
    chicago95
  ];

  # Portal and dconf management
  programs.dconf.enable = true;


}