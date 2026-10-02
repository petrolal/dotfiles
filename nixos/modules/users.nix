{ config, pkgs, ... }:

{
  # User petrolal
  users.users."petrolal" = {
    isNormalUser = true;
    description = "Petrola Lucas";
    extraGroups = [ "networkmanager" "wheel" ];
    packages = with pkgs; [
      thunderbird
      google-chrome
    ];
  };

  programs.firefox.enable = false;
}
