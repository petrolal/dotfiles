{ config, pkgs, lib, ... }:

let
  localHardware = ../hardware-configuration.nix;
  etcHardware = /etc/nixos/hardware-configuration.nix;
in
{
  imports = [
    (if builtins.pathExists localHardware then localHardware else etcHardware)
  ];

  boot.loader.systemd-boot.enable = true;
  
  # Keep the boot menu short: only the 5 most recent generations.
  boot.loader.systemd-boot.configurationLimit = 5;
  boot.loader.efi.canTouchEfiVariables = true;

  # Firmare 
  hardware.enableRedistributableFirmware = true;
  hardware.enableAllFirmware = true;
  nixpkgs.config.allowUnfree = true;
}
