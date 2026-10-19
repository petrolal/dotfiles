{ config, pkgs, ... }:

let
  redmond97 = pkgs.stdenvNoCC.mkDerivation {
    pname = "redmond97";
    version = "unstable-2021-02-23";
    src = pkgs.fetchFromGitHub {
      owner = "matthewmx86";
      repo = "Redmond97";
      rev = "f33bc057a01f091de774adbbf6abe226f0cb46e0";
      hash = "sha256-lWRLgwIlfKd01vsBq6hpEio8eAx/FrWrNnAzpHOk6Xc=";
    };
    dontBuild = true;
    installPhase = ''
      runHook preInstall
      mkdir -p $out/share/themes $out/share/icons
      cp -r Theme/csd/* $out/share/themes/
      cp -r Extras/Icons/Redmond97 $out/share/icons/
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
        name = "Redmond97";
        package = redmond97;
      };
      iconTheme = {
        name = "Chicago95";
        package = pkgs.chicago95;
      };
      cursorTheme = {
        name = "Adwaita";
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

  # Global Theme & Toolkit Overrides (Windows 98 / Redmond97)
  environment.variables = {
    GTK_THEME = "Redmond97";
    GTK_OVERLAY_SCROLLING = "0";
    QT_STYLE_OVERRIDE = "Windows";
  };

  environment.systemPackages = with pkgs; [
    adwaita-icon-theme
    gnome-themes-extra
    chicago95
    redmond97
    xfce4-whiskermenu-plugin
    wofi
    waybar
  ];

  # Portal and dconf management
  programs.dconf.enable = true;
}