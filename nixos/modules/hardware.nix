{ config, pkgs, lib, ... }:

{
  imports = [
    /etc/nixos/hardware-configuration.nix
  ];

  boot.loader.systemd-boot.enable = true;
  # Keep the boot menu short: only the 5 most recent generations.
  boot.loader.systemd-boot.configurationLimit = 5;
  boot.loader.efi.canTouchEfiVariables = true;

  networking.hostName = lib.mkDefault "abatedouro-de-anoes-PC";
  
  # Override with mkForce to eliminate conflicts
  networking.wireless.enable = lib.mkForce false;
  networking.networkmanager.enable = true;
}