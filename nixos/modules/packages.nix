{ pkgs, ... }:

let
  dynamicLibs = with pkgs; [
    libx11
    libGL
  ];

  buildTools = with pkgs; [
    gnumake
    git
    sbcl
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

in {
  environment.systemPackages =
    buildTools
    ++ wallpaperTools
    ++ devTools
    ++ systemTools
    ++ mediaUtils;

  # Lets unpatched/FHS binaries (AppImages, etc.) find dynamic libs without
  # polluting every process on the system via a global LD_LIBRARY_PATH.
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = dynamicLibs;
}
