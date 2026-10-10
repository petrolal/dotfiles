{ config, pkgs, lib, ... }:

let
  username = config.dotfiles.username;
  userDesc = config.dotfiles.userDescription;
in
{
  options.dotfiles = {
    username = lib.mkOption {
      type = lib.types.str;
      default = "petrolal";
      description = "Primary user account name.";
    };
    userDescription = lib.mkOption {
      type = lib.types.str;
      default = "Petrola Lucas";
      description = "Full user description.";
    };
  };

  config = {
    users.users.${username} = {
      isNormalUser = true;
      description = userDesc;
      extraGroups = [ "networkmanager" "wheel" "docker" ];
      shell = pkgs.fish;
      packages = with pkgs; [
        thunderbird
        google-chrome
      ];
    };

    # Default login/interactive shell. Enabling the module registers fish in
    # environment.shells and wires direnv's fish hook automatically (see
    # programs.direnv in development.nix). Per-user config lives in
    # config/fish/ (deployed to ~/.config/fish/ by the Java invoker).
    programs.fish.enable = true;

    # Allow wheel group to run sudo without password prompts
    security.sudo.wheelNeedsPassword = false;

    programs.firefox.enable = false;
  };
}
