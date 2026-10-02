# shell.nix --- Per-project Go toolchain (the Nix replacement for gvm)
#
# Enter with `nix-shell`, or automatically via direnv (`use nix` in .envrc).
# Switch the Go version without editing this file:
#   nix-shell --argstr go 1.27
# Available versions: anything nixpkgs ships as go_1_NN (1.26, 1.27, ...).

{ pkgs ? import <nixpkgs> { }
, go ? "1.26"
, ...
}:

let
  golang = pkgs."go_${builtins.replaceStrings [ "." ] [ "_" ] go}";
in
pkgs.mkShell {
  packages = [
    golang
    pkgs.gopls

    # Uncomment what the project needs:
    # pkgs.delve
    # pkgs.golangci-lint
  ];

  # Keep downloaded modules and `go install` binaries inside the project.
  # DEV_STATE_DIR moves them elsewhere (`devshell` uses ~/.local/share/devshell).
  shellHook = ''
    export GOPATH="''${DEV_STATE_DIR:-$PWD}/.go"
    export PATH="$GOPATH/bin:$PATH"
    echo "🐹 Go ${golang.version} loaded"
  '';
}
