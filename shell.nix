{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    gnumake
    sbcl
    git
  ];

  shellHook = ''
    echo "⛧ [Infernal Mainframe Shell Loaded]"
    echo "Commands available: make, make nix-switch, sbcl"
  '';
}