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

  # mkLangInit --- single-language variant of dev-init below: drops that
  # language's shell.nix + .envrc into the current directory, pinned to
  # VERSION (default: `default`), and allows it in direnv. Generates the
  # jvm-init/node-init/go-init/python-init commands, which used to be four
  # hand-copied scripts differing only in these four values.
  mkLangInit = { name, attr, label, default, template }:
    pkgs.writeShellScriptBin name ''
      set -eu
      version="''${1:-${default}}"
      for f in shell.nix .envrc; do
        if [ -e "$f" ]; then
          echo "${name}: $f already exists, refusing to overwrite" >&2
          exit 1
        fi
      done
      ${pkgs.gnused}/bin/sed "s/${attr} ? \"${default}\"/${attr} ? \"$version\"/" \
        ${template}/shell.nix > shell.nix
      install -m 644 ${template}/.envrc .envrc
      ${pkgs.direnv}/bin/direnv allow .
      echo "${name}: ${label} $version environment created; it loads on cd."
    '';

  # jvm-init [VERSION] --- drop the JVM shell.nix + .envrc into the current
  # directory, pinned to JDK VERSION (default 25), and allow it in direnv.
  jvm-init = mkLangInit {
    name = "jvm-init";
    attr = "java";
    label = "JDK";
    default = "25";
    template = jvmTemplate;
  };

  # node-init [VERSION] --- drop the Node.js shell.nix + .envrc into the
  # current directory, pinned to Node.js VERSION (default 22), and allow it in
  # direnv.
  node-init = mkLangInit {
    name = "node-init";
    attr = "node";
    label = "Node.js";
    default = "22";
    template = nodeTemplate;
  };

  # go-init [VERSION] --- drop the Go shell.nix + .envrc into the current
  # directory, pinned to Go VERSION (default 1.26), and allow it in direnv.
  go-init = mkLangInit {
    name = "go-init";
    attr = "go";
    label = "Go";
    default = "1.26";
    template = goTemplate;
  };

  # python-init [VERSION] --- drop the Python shell.nix + .envrc into the
  # current directory, pinned to Python VERSION (default 3.13), and allow it
  # in direnv.
  python-init = mkLangInit {
    name = "python-init";
    attr = "python";
    label = "Python";
    default = "3.13";
    template = pythonTemplate;
  };

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

  # General-purpose JVM build tooling, independent of any specific IDE or
  # project template above. Pinned to pkgs.jdk21, the one global JDK this
  # system exposes (see modules/jetbrains.nix, which sets JAVA_HOME/PATH to
  # it) -- not to a project's own `jvm-init <version>` pin.
  buildTools = [
    (pkgs.gradle.override { java = pkgs.jdk21; })
    (pkgs.maven.override { jdk_headless = pkgs.jdk21; })
    pkgs.lombok
    pkgs.tomcat10
  ];
in
{
  # `<nixpkgs>` resolves through the flake registry (pinned to this system's
  # nixpkgs), so nix-shell needs flakes enabled.
  nix.settings.experimental-features = [
    "nix-command"
    "flakes"
  ];

  # Hooks into fish, bash, and zsh automatically; nix-direnv is enabled by default
  # and provides `use nix` with caching.
  programs.direnv.enable = true;

  environment.systemPackages = [
    jvm-init
    node-init
    go-init
    python-init
    dev-init
    devshell
  ]
  ++ buildTools;
}
