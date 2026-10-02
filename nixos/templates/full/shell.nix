# shell.nix --- Every toolchain at once, composed from the other templates
#
# Each folder under `templates` with a shell.nix (jvm, node, python, go, ...)
# is merged in automatically: adding a language = adding a folder, nothing
# to change here. Version arguments pass through to the matching template:
#   nix-shell --argstr java 17 --argstr node 24 --argstr python 3.12 --argstr go 1.27

{ pkgs ? import <nixpkgs> { }
, templates ? ./..
, ...
}@args:

let
  isTemplate = name: type:
    type == "directory"
    && name != "full"
    && builtins.pathExists (templates + "/${name}/shell.nix");

  names = builtins.attrNames
    (pkgs.lib.filterAttrs isTemplate (builtins.readDir templates));

  shells = map
    (name: import (templates + "/${name}/shell.nix") (args // { inherit pkgs; }))
    names;
in
pkgs.mkShell {
  # Merges packages and shellHooks of every template.
  inputsFrom = shells;
}
