{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    gradle
    graalvmPackages.graalvm-ce
    git
  ];

  shellHook = ''
    echo "⛧ [Infernal Mainframe Shell Loaded]"
    echo "Commands available: ./gradlew, ./gradlew nix-switch, native-image"
  '';
}