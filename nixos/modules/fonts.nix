{ config, pkgs, ... }:

{
  # Nerd Fonts — patched developer fonts with icons and glyphs
  fonts.packages = with pkgs.nerd-fonts; [
    jetbrains-mono
    fira-code
    hack
    meslo-lg
  ] ++ (with pkgs; [
    # Broad Unicode fallback (CJK, emoji, symbols) so unsupported glyphs
    # don't render as tofu boxes in GTK apps, terminals, etc.
    noto-fonts
    noto-fonts-cjk-sans
    noto-fonts-color-emoji
  ]);

  # Enable font directory and fontconfig for proper font discovery
  fonts.fontDir.enable = true;
  fonts.fontconfig.enable = true;
}
