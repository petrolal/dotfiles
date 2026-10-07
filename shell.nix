{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    gnumake
    graalvmPackages.graalvm-ce
    git
  ];

  shellHook = ''
    echo "⛧ [Infernal Mainframe Shell Loaded]"
    echo "Commands available: make, make nix-switch, native-image"
  '';
}