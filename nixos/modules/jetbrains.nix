# JetBrains Toolbox manages JetBrains IDEs (IntelliJ IDEA, etc.) in user space
# (~/.local/share/JetBrains/Toolbox), replacing the nixpkgs-packaged
# jetbrains.idea-oss installation.
{ pkgs, ... }:

let
  # 1. nixpkgs' jetbrains-toolbox hardcodes `https://download-cdn.jetbrains.com/...`,
  #    which returns HTTP 404 without the CloudFront query-string signature that
  #    `https://download.jetbrains.com/...` attaches via 302 redirect. Rewrite
  #    the host in `fetchzip` so fresh builds fetch through the redirecting
  #    endpoint.
  # 2. nixpkgs' default `runScript` executes `${src}/bin/jetbrains-toolbox`
  #    directly from `/nix/store`. Because `/nix/store` is read-only, Toolbox
  #    displays a "Cannot install the update. The Toolbox App is installed in a
  #    directory /nix/store/... that cannot be modified" warning whenever a
  #    newer Toolbox release exists upstream. Staging `${src}/bin` into
  #    `~/.local/share/JetBrains/Toolbox/bin` (Toolbox's own standard Linux
  #    install path) gives it a user-writable installation directory so self-
  #    updates succeed cleanly inside the FHS environment.
  jetbrains-toolbox = pkgs.jetbrains-toolbox.override {
    fetchzip =
      args:
      pkgs.fetchzip (
        args
        // {
          url = builtins.replaceStrings [
            "https://download-cdn.jetbrains.com/"
          ] [ "https://download.jetbrains.com/" ] args.url;
        }
      );
    buildFHSEnv =
      args:
      pkgs.buildFHSEnv (
        args
        // {
          runScript = pkgs.writeShellScript "jetbrains-toolbox-wrapper" ''
            toolbox_dir="''${XDG_DATA_HOME:-$HOME/.local/share}/JetBrains/Toolbox/bin"
            if [ ! -x "$toolbox_dir/jetbrains-toolbox" ]; then
              mkdir -p "$(dirname "$toolbox_dir")"
              cp -r "${args.passthru.src}/bin" "$toolbox_dir"
              chmod -R u+w "$toolbox_dir"
            fi
            exec "$toolbox_dir/jetbrains-toolbox" --update-failed "$@"
          '';
        }
      );
  };
in
{
  environment.systemPackages = [
    jetbrains-toolbox
    pkgs.jdk21
  ];

  # Pinned globally per explicit request, as the default JDK for IntelliJ
  # project SDKs. This sits alongside, not instead of, modules/development.nix's
  # per-project `jvm-init <version>` direnv flow -- if a project needs a
  # different default `java` on PATH than this, pin that project's own
  # shell.nix rather than changing this.
  environment.variables.JAVA_HOME = "${pkgs.jdk21}/lib/openjdk";

  # Unpatched JetBrains IDE binaries -- both IDEs downloaded by JetBrains
  # Toolbox into ~/.local/share/JetBrains/Toolbox and IDE instances pulled by
  # Gradle's `org.jetbrains.intellij.platform` plugin (`./gradlew runIde`) into
  # ~/.gradle/caches -- bundle a prebuilt JetBrains Runtime (JBR), JCEF, and
  # native helpers that expect FHS paths.
  #
  # nix-ld provides the dynamic linker and libraries for those unpatched
  # binaries when launched directly (e.g. via Toolbox-generated .desktop files,
  # CLI scripts, or Gradle tasks).
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = with pkgs; [
    libx11
    libxext
    libxrender
    libxtst
    libxi
    libxrandr
    libxcursor
    libxfixes
    libxcomposite
    libxdamage
    libxcb
    libxxf86vm
    libxkbcommon
    wayland
    freetype
    fontconfig
    expat
    zlib
    stdenv.cc.cc.lib
    glib
    gtk3
    cairo
    pango
    atk
    at-spi2-core
    at-spi2-atk
    dbus
    libdrm
    libgbm
    mesa
    alsa-lib
    cups
    nspr
    nss
    udev
    e2fsprogs
  ];
}
