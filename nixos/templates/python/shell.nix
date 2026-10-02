# shell.nix --- Per-project Python toolchain (the Nix replacement for pyenv)
#
# Enter with `nix-shell`, or automatically via direnv (`use nix` in .envrc).
# Switch the Python version without editing this file:
#   nix-shell --argstr python 3.12
# Available versions: anything nixpkgs ships as pythonXY (3.11 ... 3.15).

{ pkgs ? import <nixpkgs> { }
, python ? "3.13"
, ...
}:

let
  py = pkgs."python${builtins.replaceStrings [ "." ] [ "" ] python}";
in
pkgs.mkShell {
  packages = [
    py
    pkgs.uv

    # Uncomment what the project needs:
    # pkgs.pyright
    # pkgs.ruff
  ];

  # The Nix store is read-only, so `pip install` goes to a venv that is
  # created on entry and activated. It is rebuilt when the Python version
  # changes. DEV_STATE_DIR moves it elsewhere (`devshell` uses ~/.local/share/devshell).
  #
  # Prebuilt wheels from PyPI (numpy, pandas, ...) expect libstdc++ and zlib in
  # standard paths, which NixOS lacks; LD_LIBRARY_PATH points them at Nix's.
  # uv must use this Python: the builds it downloads do not run on NixOS.
  shellHook = ''
    export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath [ pkgs.stdenv.cc.cc.lib pkgs.zlib ]}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    export UV_PYTHON_DOWNLOADS=never

    venv="''${DEV_STATE_DIR:-$PWD}/.venv"
    if ! grep -qx "version = ${py.version}" "$venv/pyvenv.cfg" 2>/dev/null; then
      ${py}/bin/python -m venv --clear "$venv"
    fi
    source "$venv/bin/activate"
    echo "🐍 Python ${py.version} loaded (venv: $venv)"
  '';
}
