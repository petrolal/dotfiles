{ config, pkgs, ... }:

{
  # Nerd Fonts — patched developer fonts with icons and glyphs
  fonts.packages = with pkgs.nerd-fonts; [
    jetbrains-mono
    fira-code
    hack
    meslo-lg
  ];

  # Enable font directory and fontconfig for proper font discovery
  fonts.fontDir.enable = true;
  fonts.fontconfig.enable = true;
}
