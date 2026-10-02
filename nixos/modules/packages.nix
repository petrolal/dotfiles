{ config, pkgs, ... }:

{
  nixpkgs.config.allowUnfree = true;

  environment.systemPackages = with pkgs; [
    # Build & Orchestration
    gnumake
    sbcl
    git

    # Video Wallpaper & Wallpapers
    mpv
    xwinwrap
    feh

    # System & Terminal Utilities
    emacs
    wget
    curl
    pciutils
    htop
    fastfetch
    xclip
    claude-code

    # Utils and good tools
    shotcut
  ];
}