{ config, pkgs, lib, ... }:

{
  networking.hostName = lib.mkDefault "abatedouro-de-anoes-PC";
  
  # Override with mkForce to eliminate conflicts
  networking.wireless.enable = lib.mkForce false;
  networking.networkmanager.enable = true;

  services.openssh.enable = true;
}
