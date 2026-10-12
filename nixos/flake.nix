{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    antigravity-nix = {
      url = "github:jacopone/antigravity-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, antigravity-nix, ... }:
    let
      system = "x86_64-linux";

      # networking.hostName is per-machine, same as hardware-configuration.nix
      # (see modules/hardware.nix) -- it must never be a value hardcoded into
      # this repo. /etc/hostname is whatever nixos-generate-config / the
      # installer already set for this specific install; read it instead of
      # overwriting a real machine's identity with a literal string on every
      # rebuild. Falls back to "nixos" (NixOS's own installer default) only
      # when evaluating this flake somewhere without a real /etc/hostname at
      # all (e.g. `nix build --impure` from a plain clone).
      detectedHostname =
        if builtins.pathExists /etc/hostname
        then nixpkgs.lib.strings.trim (builtins.readFile /etc/hostname)
        else "nixos";

      mkConfig = { hostname ? detectedHostname, user ? "petrolal", extraModules ? [] }:
        nixpkgs.lib.nixosSystem {
          modules = [
            { nixpkgs.hostPlatform = system; }
            ./configuration.nix

            ({ pkgs, ... }: {
              # allowUnfree lives in modules/hardware.nix (single source of truth).
              networking.hostName = hostname;
              dotfiles.username = user;

              environment.systemPackages = [
                antigravity-nix.packages.${pkgs.stdenv.hostPlatform.system}.google-antigravity-cli
              ];
            })
          ] ++ extraModules;
        };
    in
    {
      nixosConfigurations = {
        # Primary workstation host
        "abatedouro-de-anoes-PC" = mkConfig {
          hostname = "abatedouro-de-anoes-PC";
          user = "petrolal";
        };

        # Generic / portable fallback host: hostname is whatever this specific
        # install's /etc/hostname already says (see detectedHostname above),
        # never a hardcoded literal.
        default = mkConfig {
          user = "petrolal";
        };
      }
      # `nixos-rebuild switch --flake <dir>` (no #attr) looks up the config by
      # this machine's actual /etc/hostname, not by the "default" key above.
      # Alias that hostname to the same fallback config so the lookup
      # resolves on any machine that hasn't been given a dedicated entry yet.
      // nixpkgs.lib.optionalAttrs (detectedHostname != "abatedouro-de-anoes-PC") {
        "${detectedHostname}" = mkConfig {
          user = "petrolal";
        };
      };
    };
}
