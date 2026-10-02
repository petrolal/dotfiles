{ config, pkgs, ... }:

{
  imports = [
    ./modules/hardware.nix
    ./modules/desktop.nix
    ./modules/nvidia.nix
    ./modules/packages.nix
    ./modules/development.nix
  ];

  system.stateVersion = "26.05";
}