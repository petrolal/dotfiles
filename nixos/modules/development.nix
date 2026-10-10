{ config, pkgs, ... }:

# Per-project toolchains the Nix way: each project carries a shell.nix and
# direnv loads it on `cd`. Replaces version managers such as SDKMAN!, nvm,
# pyenv and gvm.
let
  jvmTemplate = ../templates/jvm;
  nodeTemplate = ../templates/node;
  goTemplate = ../templates/go;
  pythonTemplate = ../templates/python;
  templates = ../templates;

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

  # node-init [VERSION] --- drop the Node.js shell.nix + .envrc into the
  # current directory, pinned to Node.js VERSION (default 22), and allow it in
  # direnv.
  node-init = pkgs.writeShellScriptBin "node-init" ''
    set -eu
    version="''${1:-22}"
    for f in shell.nix .envrc; do
      if [ -e "$f" ]; then
        echo "node-init: $f already exists, refusing to overwrite" >&2
        exit 1
      fi
    done
    ${pkgs.gnused}/bin/sed "s/node ? \"22\"/node ? \"$version\"/" \
      ${nodeTemplate}/shell.nix > shell.nix
    install -m 644 ${nodeTemplate}/.envrc .envrc
    ${pkgs.direnv}/bin/direnv allow .
    echo "node-init: Node.js $version environment created; it loads on cd."
  '';

  # go-init [VERSION] --- drop the Go shell.nix + .envrc into the current
  # directory, pinned to Go VERSION (default 1.26), and allow it in direnv.
  go-init = pkgs.writeShellScriptBin "go-init" ''
    set -eu
    version="''${1:-1.26}"
    for f in shell.nix .envrc; do
      if [ -e "$f" ]; then
        echo "go-init: $f already exists, refusing to overwrite" >&2
        exit 1
      fi
    done
    ${pkgs.gnused}/bin/sed "s/go ? \"1.26\"/go ? \"$version\"/" \
      ${goTemplate}/shell.nix > shell.nix
    install -m 644 ${goTemplate}/.envrc .envrc
    ${pkgs.direnv}/bin/direnv allow .
    echo "go-init: Go $version environment created; it loads on cd."
  '';

  # python-init [VERSION] --- drop the Python shell.nix + .envrc into the
  # current directory, pinned to Python VERSION (default 3.13), and allow it
  # in direnv.
  python-init = pkgs.writeShellScriptBin "python-init" ''
    set -eu
    version="''${1:-3.13}"
    for f in shell.nix .envrc; do
      if [ -e "$f" ]; then
        echo "python-init: $f already exists, refusing to overwrite" >&2
        exit 1
      fi
    done
    ${pkgs.gnused}/bin/sed "s/python ? \"3.13\"/python ? \"$version\"/" \
      ${pythonTemplate}/shell.nix > shell.nix
    install -m 644 ${pythonTemplate}/.envrc .envrc
    ${pkgs.direnv}/bin/direnv allow .
    echo "python-init: Python $version environment created; it loads on cd."
  '';

  # dev-init [LANG...] --- compose a multi-language environment in the current
  # directory: copies each LANG template (default: all of them) into
  # nix/<LANG>/shell.nix, writes a shell.nix that merges everything under nix/,
  # and allows it in direnv. Versions are the defaults in each nix/<LANG>/shell.nix.
  dev-init = pkgs.writeShellScriptBin "dev-init" ''
    set -eu
    available=$(cd ${templates} && for d in */; do
      d=''${d%/}
      [ "$d" != full ] && [ -e "$d/shell.nix" ] && echo "$d"
    done)
    langs="''${*:-$available}"
    for f in shell.nix .envrc nix; do
      if [ -e "$f" ]; then
        echo "dev-init: $f already exists, refusing to overwrite" >&2
        exit 1
      fi
    done
    for l in $langs; do
      if [ ! -e "${templates}/$l/shell.nix" ]; then
        echo "dev-init: unknown language '$l' (available:" $available")" >&2
        exit 1
      fi
    done
    for l in $langs; do
      install -D -m 644 "${templates}/$l/shell.nix" "nix/$l/shell.nix"
    done
    ${pkgs.gnused}/bin/sed 's|templates ? \./\.\.|templates ? ./nix|' \
      ${templates}/full/shell.nix > shell.nix
    install -m 644 ${templates}/full/.envrc .envrc
    ${pkgs.direnv}/bin/direnv allow .
    echo "dev-init: environment with" $langs "created; it loads on cd."
  '';

  # devshell [--argstr NAME VERSION ...] --- enter the shell with every
  # template from any directory. venv, GOPATH and npm -g live in
  # ~/.local/share/devshell instead of the current directory.
  # Drops into fish (or $SHELL) interactively so users keep their chosen shell.
  devshell = pkgs.writeShellScriptBin "devshell" ''
    export DEV_STATE_DIR="''${XDG_DATA_HOME:-$HOME/.local/share}/devshell"
    mkdir -p "$DEV_STATE_DIR"
    user_shell="''${SHELL:-${pkgs.fish}/bin/fish}"
    exec nix-shell ${templates}/full/shell.nix "$@" --command "exec $user_shell"
  '';
in
{
  # `<nixpkgs>` resolves through the flake registry (pinned to this system's
  # nixpkgs), so nix-shell needs flakes enabled.
  nix.settings.experimental-features = [ "nix-command" "flakes" ];

  # Hooks into bash; nix-direnv is enabled by default and provides `use nix`
  # with caching.
  programs.direnv.enable = true;

  environment.systemPackages = [ jvm-init node-init go-init python-init dev-init devshell ];
}
