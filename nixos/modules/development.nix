{ config, pkgs, ... }:

# Per-project toolchains the Nix way: each project carries a shell.nix and
# direnv loads it on `cd`. Replaces version managers such as SDKMAN!.
let
  jvmTemplate = ../templates/jvm;

  # jvm-init [VERSION] --- drop the JVM shell.nix + .envrc into the current
  # directory, pinned to JDK VERSION (default 21), and allow it in direnv.
  jvm-init = pkgs.writeShellScriptBin "jvm-init" ''
    set -eu
    version="''${1:-21}"
    for f in shell.nix .envrc; do
      if [ -e "$f" ]; then
        echo "jvm-init: $f already exists, refusing to overwrite" >&2
        exit 1
      fi
    done
    ${pkgs.gnused}/bin/sed "s/java ? \"21\"/java ? \"$version\"/" \
      ${jvmTemplate}/shell.nix > shell.nix
    install -m 644 ${jvmTemplate}/.envrc .envrc
    ${pkgs.direnv}/bin/direnv allow .
    echo "jvm-init: JDK $version environment created; it loads on cd."
  '';
in
{
  # `<nixpkgs>` resolves through the flake registry (pinned to this system's
  # nixpkgs), so nix-shell needs flakes enabled.
  nix.settings.experimental-features = [ "nix-command" "flakes" ];

  # Hooks into bash; nix-direnv is enabled by default and provides `use nix`
  # with caching.
  programs.direnv.enable = true;

  environment.systemPackages = [ jvm-init ];
}
