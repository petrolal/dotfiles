{ config, pkgs, ... }:

let
  imp98 = pkgs.stdenvNoCC.mkDerivation {
    pname = "imp98";
    version = "1.0.0";
    src = ../../themes/Imp98;
    dontBuild = true;
    installPhase = ''
      runHook preInstall
      mkdir -p $out/share/themes/Imp98
      cp -r . $out/share/themes/Imp98/
      runHook postInstall
    '';
  };
in
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

  # X11 & XFCE
  services.xserver.enable = true;
  services.xserver.displayManager.lightdm = {
    enable = true;
    greeters.gtk = {
      theme = {
        name = "Imp98";
        package = imp98;
      };
      iconTheme = {
        name = "Chicago95";
        package = pkgs.chicago95;
      };
      cursorTheme = {
        name = "Chicago95_Cursor_Black";
        package = pkgs.chicago95;
        size = 24;
      };
      extraConfig = ''
        user-background = false
      '';
    };
  };
  services.xserver.desktopManager.xfce.enable = true;
  services.xserver.desktopManager.xterm.enable = false;
  services.xserver.excludePackages = [ pkgs.xterm ];

  services.xserver.xkb = {
    layout = "us";
    variant = "intl";
  };
  console.keyMap = "us-acentos";

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

  # Global Theme & Toolkit Overrides (Windows 98 / Imp98)
  environment.variables = {
    GTK_OVERLAY_SCROLLING = "0";
    QT_STYLE_OVERRIDE = "Windows";
  };

  environment.systemPackages = with pkgs; [
    chicago95
    imp98
    xfce4-whiskermenu-plugin
  ];

  # Portal and dconf management
  programs.dconf.enable = true;

  # Bluetooth manager with tray applet (icon shows in the XFCE panel's
  # notification area via the applet's XDG autostart entry)
  services.blueman.enable = true;
}