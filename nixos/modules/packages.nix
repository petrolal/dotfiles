{ config, pkgs, ... }:

{
  nixpkgs.config.allowUnfree = true;

  environment.systemPackages = with pkgs; [
    # Build & Orchestration
    gnumake
    sbcl
    git

    # Video Wallpaper & Terminal Aesthetics
    mpv
    xwinwrap
    cool-retro-term
    feh

    # System & Terminal Utilities
    emacs
    wget
    curl
    pciutils
    htop
    fastfetch
  ];
}