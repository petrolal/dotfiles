# This flake packages dotfiles-deployer, the Java CLI in src/ (see
# nix/dotfiles-invoker.nix), separately from nixos/flake.nix, which owns
# this machine's nixosConfigurations. The two are independent concerns: one
# is "what runs this repo's deploy/wallpaper tooling", the other is "what
# NixOS system this machine boots".
{
  description = "dotfiles-deployer: Java CLI that links dotfiles and runs the OpenGL wallpaper daemon";

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

      # Full dev loop (Gradle + tests + `./gradlew nativeCompile` with
      # -march=native, the default task's whole task graph, etc.) still
      # needs network for the test-only Maven Central deps -- that's what
      # this devShell is for, same packages shell.nix already provided,
      # just reachable via `nix develop` too.
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.gradle
          pkgs.graalvmPackages.graalvm-ce
          pkgs.git
        ];

        shellHook = ''
          echo "[dotfiles-deployer dev shell] ./gradlew, native-image, gradle available"
        '';
      };

      formatter.${system} = pkgs.nixfmt;
    };
}
