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

  # Portal and dconf management
  programs.dconf.enable = true;

  # Bluetooth manager with tray applet (consumed by AGS's own tray widget
  # under Hyprland, see hyprland.nix)
  services.blueman.enable = true;

  # Hardware-accelerated screen recorder (ShadowPlay-style instant replay & capture)
  programs.gpu-screen-recorder = {
    enable = true;
    ui.enable = true;
  };
}