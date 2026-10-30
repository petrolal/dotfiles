{ config, pkgs, ... }:

{
  imports = [
    ./modules/hardware.nix
    ./modules/networking.nix
    ./modules/desktop.nix
    ./modules/hyprland.nix
    ./modules/nvidia.nix
    ./modules/fonts.nix
    ./modules/users.nix
    ./modules/packages.nix
    ./modules/nix-ld.nix
    ./modules/development.nix
    ./modules/jetbrains.nix
    ./modules/containers.nix
    ./modules/flatpak.nix
    ./modules/ollama.nix
  ];

  system.stateVersion = "26.05";
}
