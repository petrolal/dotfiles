{ config, pkgs, ... }:

{
  # Hostname is set per-config in flake.nix's mkConfig (one source of truth).

  networking.networkmanager.enable = true;

  services.resolved.enable = true;
  services.openssh.enable = true;
}
