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
      system = builtins.currentSystem;

      hostname =
        let
          hostnamePath = /etc/hostname;
        in
        if builtins.pathExists hostnamePath then
          nixpkgs.lib.removeSuffix "\n" (builtins.readFile hostnamePath)
        else
          "default";
    in
    {
      nixosConfigurations = {
        ${hostname} = nixpkgs.lib.nixosSystem {
          modules = [
            { nixpkgs.hostPlatform = system; }
            ./configuration.nix

            ({ pkgs, ... }: {
              nixpkgs.config.allowUnfree = true;
              networking.hostName = hostname;

              environment.systemPackages = [
                antigravity-nix.packages.${pkgs.system}.google-antigravity-cli
              ];
            })
          ];
        };
      };
    };
}