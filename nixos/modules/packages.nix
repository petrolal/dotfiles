{ pkgs, ... }:

let
  buildTools = with pkgs; [
    gnumake
    git
    sbcl
    libx11
    libGL
  ];

  LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath (with pkgs; [
    libx11
    libGL
  ]);

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

  environment.variables.LD_LIBRARY_PATH = LD_LIBRARY_PATH;
}
