{ pkgs, ... }:

{
    # Enable Flatpak
    services.flatpak.enable = true;

    # Install only if have internet
    systemd.services.flatpak-managed-install = {
      wants = [ "network-online.target" ];
      after = [ "network-online.target" ];
    };

    # Declare flatpak packages
    services.flatpak.packages = [
        "net.sapples.LiveCaptions"
    ];
}