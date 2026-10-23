{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    antigravity-nix = {
      url = "github:jacopone/antigravity-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    nix-flatpak.url = "github:gmodena/nix-flatpak/?ref=latest";
  };

  outputs = { self, nixpkgs, antigravity-nix, nix-flatpak, ... }:
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

      # `user` defaults to null here deliberately: modules/users.nix's
      # `dotfiles.username` option default is the single source of truth for
      # "petrolal" as the normal account name. Duplicating that default here
      # would let the two silently diverge.
      mkConfig = { hostname ? detectedHostname, user ? null, extraModules ? [] }:
        nixpkgs.lib.nixosSystem {
          modules = [
            { nixpkgs.hostPlatform = system; }
            nix-flatpak.nixosModules.nix-flatpak
            ./configuration.nix

            ({ pkgs, ... }: {
              # allowUnfree lives in modules/hardware.nix (single source of truth).
              networking.hostName = hostname;

              environment.systemPackages = [
                antigravity-nix.packages.${pkgs.stdenv.hostPlatform.system}.google-antigravity-cli
              ];
            })
          ] ++ nixpkgs.lib.optional (user != null) { dotfiles.username = user; }
            ++ extraModules;
        };
      # Primary (and, for now, only real) workstation host: has a discrete
      # NVIDIA GPU with Intel/NVIDIA PRIME offload, so dotfiles.nvidia is
      # enabled only here (see modules/nvidia.nix) -- the bus IDs are this
      # machine's PCI topology and must never apply to another host built
      # from this same flake. `mkConfig` forces `networking.hostName` to this
      # literal regardless of what detectedHostname currently reads, so
      # switching to it also self-heals a drifted /etc/hostname rather than
      # perpetuating it (see the 2026-10-14 hostname-reverted-to-"nixos"
      # incident: `nixos-rebuild switch` with no #attr resolves by the *live*
      # hostname command, so once it ever drifted to the installer's generic
      # "nixos", a bare switch kept matching the old generic fallback config
      # below and re-setting hostName back to "nixos" forever).
      primary = mkConfig {
        hostname = "abatedouro-de-anoes-PC";
        extraModules = [
          {
            dotfiles.nvidia = {
              enable = true;
              prime = {
                intelBusId = "PCI:0:2:0";
                nvidiaBusId = "PCI:1:0:0";
              };
            };
          }
        ];
      };
    in
    {
      nixosConfigurations = {
        "abatedouro-de-anoes-PC" = primary;

        # `nixos-rebuild switch --flake <dir>` (no #attr) looks up the config
        # by this machine's *actual*, live hostname -- not by this "default"
        # key. Since this flake only has one real host, both `default` and
        # whatever hostname this install currently reports (correct or
        # drifted) alias to the same `primary` config: a correct hostname
        # resolves it directly, and a drifted one gets corrected back to it
        # on the next switch instead of reinforcing the drift.
        default = primary;
      } // nixpkgs.lib.optionalAttrs (detectedHostname != "abatedouro-de-anoes-PC") {
        "${detectedHostname}" = primary;
      };

      formatter.${system} = nixpkgs.legacyPackages.${system}.nixfmt;

      checks.${system}.statix =
        nixpkgs.legacyPackages.${system}.runCommand "statix-check" { } ''
          ${nixpkgs.legacyPackages.${system}.statix}/bin/statix check --config ${./.} ${./.}
          touch $out
        '';
    };
}
