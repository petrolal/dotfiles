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

      mkConfig = { hostname ? "default", user ? "petrolal", extraModules ? [] }:
        nixpkgs.lib.nixosSystem {
          modules = [
            { nixpkgs.hostPlatform = system; }
            ./configuration.nix

            ({ pkgs, ... }: {
              nixpkgs.config.allowUnfree = true;
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

        # Generic / portable fallback host
        default = mkConfig {
          hostname = "default";
          user = "petrolal";
        };
      };
    };
}
