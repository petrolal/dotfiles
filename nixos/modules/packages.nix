{ pkgs, ... }:

let
  dynamicLibs = with pkgs; [
    libx11
    libGL
  ];

  buildTools = with pkgs; [
    gnumake
    git
    graalvmPackages.graalvm-ce
  ] ++ dynamicLibs;

  wallpaperTools = with pkgs; [
    mpv
    xwinwrap
    feh
  ];

  devTools = with pkgs; [
    emacs
    claude-code
  ];

  systemTools = with pkgs; [
    pciutils
    htop
    fastfetch
    xclip
    curl
    wget
    zip
    unzip
    which
    less
    rlwrap
  ];

  mediaUtils = with pkgs; [
    shotcut
    appimage-run
  ];

  # The custom retro taskbar/panel (Config.qml, Bar.qml, etc. under
  # config/quickshell/). configPath points at the live symlinked dotfiles
  # (~/.config/quickshell) rather than package.nix's own store-copy default,
  # so editing the QML takes effect on restart without a NixOS rebuild.
  customShell = [
    (pkgs.callPackage ../../config/quickshell/package.nix {
      configPath = "/home/petrolal/.config/quickshell";
    })
  ];

in {
  environment.systemPackages =
    buildTools
    ++ wallpaperTools
    ++ devTools
    ++ systemTools
    ++ mediaUtils
    ++ customShell;

  # Lets unpatched/FHS binaries (AppImages, etc.) find dynamic libs without
  # polluting every process on the system via a global LD_LIBRARY_PATH.
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = dynamicLibs;
}
