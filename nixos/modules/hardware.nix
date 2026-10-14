{
  config,
  pkgs,
  lib,
  ...
}:

let
  # hardware-configuration.nix is inherently per-machine (disk UUIDs, fs
  # types, controller modules) and must never be treated as portable
  # dotfiles content. /etc/nixos/hardware-configuration.nix is the one
  # nixos-generate-config just wrote for *this* disk at install time, so it
  # has to win whenever it exists -- which is every real install, since
  # bootstrap.sh runs after that file is already there. The repo's checked-in
  # copy is only a fallback for evaluating/building this flake somewhere
  # without a real /etc/nixos (e.g. `nix build --impure` from a plain clone),
  # and goes stale the moment the repo is reused on different hardware or the
  # disk gets repartitioned -- see the 2026-10-12 boot-hang incident, caused
  # by this fallback silently taking priority over a freshly reinstalled
  # machine's real, correct hardware-configuration.nix.
  etcHardware = /etc/nixos/hardware-configuration.nix;
  localHardware = ../hardware-configuration.nix;
in
{
  imports = [
    (if builtins.pathExists etcHardware then etcHardware else localHardware)
  ];

  boot.loader.systemd-boot.enable = true;

  # Keep the boot menu short: only the 5 most recent generations.
  boot.loader.systemd-boot.configurationLimit = 5;
  boot.loader.efi.canTouchEfiVariables = true;

  # Firmware
  hardware.enableRedistributableFirmware = true;
  hardware.enableAllFirmware = true;
  nixpkgs.config.allowUnfree = true;

  # WebHID / QMK / VIA keyboard configurators (e.g. qmk.top, usevia.app)
  services.udev.extraRules = ''
    KERNEL=="hidraw*", SUBSYSTEM=="hidraw", MODE="0666", TAG+="uaccess", TAG+="udev-acl"
  '';
  hardware.keyboard.qmk.enable = true;

  # jetbrains.idea-oss (see modules/jetbrains.nix) carries known,
  # currently-unpatched vulnerabilities in nixpkgs (tracked as
  # NIXPKGS-2026-2269; nixpkgs itself no longer updates this package, see
  # https://tracker.security.nixos.org/issues/NIXPKGS-2026-2269 for exactly
  # what they are). Allowlisted here as an explicit, visible decision rather
  # than silently building anyway.
  nixpkgs.config.permittedInsecurePackages = [
    "intellij-idea-oss-2025.3.4"
  ];
}
