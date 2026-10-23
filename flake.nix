# This flake packages dotfiles-deployer, the Java CLI in src/ (see
# nix/dotfiles-invoker.nix), separately from nixos/flake.nix, which owns
# this machine's nixosConfigurations. The two are independent concerns: one
# is "what runs this repo's deploy/wallpaper tooling", the other is "what
# NixOS system this machine boots".
{
  description = "dotfiles-deployer: Scala 3 CLI that links dotfiles and runs the OpenGL wallpaper daemon";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in
    {
      packages.${system} = {
        dotfiles-invoker = pkgs.callPackage ./nix/dotfiles-invoker.nix { };
        default = self.packages.${system}.dotfiles-invoker;
      };

      # Full dev loop (sbt + tests + native-image) reachable via `nix develop`
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          (pkgs.sbt.override { jre = pkgs.graalvmPackages.graalvm-ce; })
          (pkgs.scala_3.override { jre = pkgs.graalvmPackages.graalvm-ce; })
          pkgs.graalvmPackages.graalvm-ce
          pkgs.git
        ];

        shellHook = ''
          echo "[dotfiles-deployer dev shell] sbt, scalac, native-image available"
        '';
      };

      formatter.${system} = pkgs.nixfmt;
    };
}
