{ config, pkgs, lib, ... }:

{
  # Hostname is set per-config in flake.nix's mkConfig (one source of truth).

  # Override with mkForce to eliminate conflicts
  networking.networkmanager.enable = lib.mkForce true;

  services.resolved.enable = true;
  services.openssh.enable = true;
}
