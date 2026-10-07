{ config, pkgs, ... }:

{
  imports = [
    ./modules/hardware.nix
    ./modules/networking.nix
    ./modules/desktop.nix
    ./modules/nvidia.nix
    ./modules/fonts.nix
    ./modules/users.nix
    ./modules/packages.nix
    ./modules/development.nix
    ./modules/eclipse.nix
  ];

  system.stateVersion = "26.05";
}