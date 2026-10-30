# NVIDIA PRIME offload is inherently per-machine (PCI bus IDs depend on the
# specific motherboard's topology), so this module is opt-in and
# parameterized instead of being baked into the shared workstation profile.
# Enable it, and set the bus IDs, from the specific host that has this GPU
# (see flake.nix's mkConfig for "abatedouro-de-anoes-PC") -- never from
# configuration.nix, which every nixosConfigurations entry imports.
{ config, lib, pkgs, ... }:

let
  cfg = config.dotfiles.nvidia;
in
{
  options.dotfiles.nvidia = {
    enable = lib.mkEnableOption "NVIDIA PRIME offload graphics";

    prime = {
      intelBusId = lib.mkOption {
        type = lib.types.str;
        description = "PCI bus ID of the integrated Intel GPU (output of `lspci | grep VGA`).";
      };
      nvidiaBusId = lib.mkOption {
        type = lib.types.str;
        description = "PCI bus ID of the discrete NVIDIA GPU (output of `lspci | grep VGA`).";
      };
    };
  };

  config = lib.mkIf cfg.enable {
    # hardware.graphics.enable/enable32Bit is already set unconditionally by
    # hyprland.nix ("Required for any Wayland compositor") -- restating the
    # same true/true here added nothing, just a second place to keep in sync.

    services.xserver.videoDrivers = [ "nvidia" ];

    hardware.nvidia = {
      modesetting.enable = true;
      powerManagement.enable = true;
      powerManagement.finegrained = true;
      open = true;
      nvidiaSettings = true;
      package = config.boot.kernelPackages.nvidiaPackages.stable;

      prime = {
        offload = {
          enable = true;
          enableOffloadCmd = true;
        };
        inherit (cfg.prime) intelBusId nvidiaBusId;
      };
    };
  };
}
