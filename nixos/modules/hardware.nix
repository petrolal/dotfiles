{ config, pkgs, lib, ... }:

{
  imports = [
    /etc/nixos/hardware-configuration.nix
  ];

  boot.loader.systemd-boot.enable = true;
  boot.loader.efi.canTouchEfiVariables = true;

  networking.hostName = lib.mkDefault "abatedouro-de-anoes-PC";
  
  # Override with mkForce to eliminate conflicts
  networking.wireless.enable = lib.mkForce false;
  networking.networkmanager.enable = true;
}