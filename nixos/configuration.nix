{ config, pkgs, ... }:

{
  imports = [
    ./modules/hardware.nix
    ./modules/desktop.nix
    ./modules/nvidia.nix
    ./modules/packages.nix
  ];

  system.stateVersion = "26.05";
}