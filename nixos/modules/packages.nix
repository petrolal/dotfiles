{ config, pkgs, ... }:

{
  environment.systemPackages = with pkgs; [
    # Build & Orchestration
    gnumake
    sbcl
    git

    # Video Wallpaper & Wallpapers
    mpv
    xwinwrap
    feh

    # Development
    emacs
    claude-code

    # System & Terminal Utilities
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

    # Utils and good tools
    shotcut
    appimage-run
  ];
}