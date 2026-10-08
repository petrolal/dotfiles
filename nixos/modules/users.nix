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
      extraGroups = [ "networkmanager" "wheel" ];
      packages = with pkgs; [
        thunderbird
        google-chrome
      ];
    };

    # Allow wheel group to run sudo without password prompts
    security.sudo.wheelNeedsPassword = false;

    programs.firefox.enable = false;
  };
}
