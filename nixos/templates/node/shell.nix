# shell.nix --- Per-project Node.js toolchain (the Nix replacement for nvm/fnm)
#
# Enter with `nix-shell`, or automatically via direnv (`use nix` in .envrc).
# Switch the Node.js major version without editing this file:
#   nix-shell --argstr node 24
# Available versions: anything nixpkgs ships as nodejs_NN (22, 24, 26, ...).

{ pkgs ? import <nixpkgs> { }
, node ? "22"
, ...
}:

let
  nodejs = pkgs."nodejs_${node}";
in
pkgs.mkShell {
  packages = [
    nodejs # includes npm and npx

    # Uncomment what the project needs:
    # (pkgs.yarn.override { inherit nodejs; })
    # pkgs.pnpm
    # pkgs.typescript
    # pkgs.typescript-language-server
  ];

  # The Nix store is read-only, so send `npm install -g` to a per-project
  # prefix instead, and expose its binaries along with local node_modules/.bin.
  # DEV_STATE_DIR moves the prefix elsewhere (`devshell` uses ~/.local/share/devshell).
  shellHook = ''
    export NPM_CONFIG_PREFIX="''${DEV_STATE_DIR:-$PWD}/.npm-global"
    export PATH="$NPM_CONFIG_PREFIX/bin:$PWD/node_modules/.bin:$PATH"
    echo "⬢ Node.js ${nodejs.version} loaded (npm $(npm --version))"
  '';
}
