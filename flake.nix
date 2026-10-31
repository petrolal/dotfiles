# Root Flake for dotfiles polyglot monorepo:
# - CLI deployer (cli/ -> packages.dotfiles-imp)
# - C++ QtQuick3D plugin (plugins/retroroom -> packages.retroroom)
# - Unified developer shells (devShells.default)
{
  description = "Dotfiles polyglot monorepo: Kotlin deployer CLI & Qt Quick 3D RetroRoom plugin";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/151fa4e8ddfdd8dd25d945ad94ed54a13de9f6e4";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in
    {
      packages.${system} = {
        dotfiles-imp = pkgs.callPackage ./nix/dotfiles-imp.nix { };
        retroroom = pkgs.callPackage ./nix/retroroom.nix { };
        default = self.packages.${system}.dotfiles-imp;
      };

      devShells.${system} = {
        default = pkgs.mkShell {
          packages = [
            pkgs.gradle
            pkgs.graalvmPackages.graalvm-ce
            pkgs.git
            pkgs.cmake
            pkgs.ninja
            pkgs.pkg-config
            pkgs.qt6.qtbase
            pkgs.qt6.qtdeclarative
            pkgs.qt6.qtquick3d
            pkgs.qt6.qtshadertools
          ];

          shellHook = ''
            echo "[dotfiles polyglot dev shell] gradle, graalvm, cmake, ninja, Qt6 available"
          '';
        };

        room = self.devShells.${system}.default;
      };

      formatter.${system} = pkgs.nixfmt;
    };
}
