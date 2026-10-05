{ config, pkgs, lib, ... }:

{   
  networking.hostName = lib.mkDefault "abatedouro-de-anoes-PC";
  
  # Override with mkForce to eliminate conflicts
  networking.networkmanager.enable = lib.mkForce true;

  services.resolved.enable = true;
  services.openssh.enable = true;
}
