{ pkgs, lib, ... }:

let
  jdk = pkgs.jdk25;

  # Kotlin has no nixpkgs Eclipse plugin and ships only as a live p2 update
  # site, so it can't be fetched/hashed at build time like the plugins in
  # eclipses.plugins. Instead, mkPluginInstalledBinary below mirrors the
  # read-only Nix store install into a writable per-user copy on first run
  # and installs it there via Eclipse's own p2 director.
  kotlinRepo = "https://files.pkg.jetbrains.space/kotlin/p/kotlin-eclipse/main/last/";
  kotlinIU = "org.jetbrains.kotlin.feature.feature.group";

  # exe (pkgRoot/exe) --- wraps an Eclipse-family binary so that, on first
  # run for a given store path, it mirrors pkgRoot into a writable
  # per-user directory (keyed by the store path, so a nixpkgs update gets
  # a fresh copy) and installs Kotlin into that copy via the p2 director.
  # The director needs a writable install to add plugins, and running it
  # from within the mirrored copy lets it resolve dependencies against
  # that copy's own already-consistent profile instead of a bare
  # repository list. Later launches reuse the prepared copy as-is; a
  # failed install is non-fatal, so Eclipse/STS still launches without
  # Kotlin.
  mkPluginInstalledBinary = { pkgRoot, exe, name }:
    pkgs.writeShellScript "${name}-with-kotlin" ''
      set -eu
      dataDir="$HOME/.local/share/eclipse/${name}-$(${pkgs.coreutils}/bin/basename ${pkgRoot})"
      if [ ! -e "$dataDir/.ready" ]; then
        echo "${name}: preparing writable install + Kotlin plugin (first run, needs network)..." >&2
        ${pkgs.coreutils}/bin/rm -rf "$dataDir.tmp"
        ${pkgs.coreutils}/bin/mkdir -p "$dataDir.tmp"
        ${pkgs.coreutils}/bin/cp -a ${pkgRoot}/. "$dataDir.tmp/"
        ${pkgs.coreutils}/bin/chmod -R u+w "$dataDir.tmp"
        if ! "$dataDir.tmp/${exe}" -nosplash \
            -application org.eclipse.equinox.p2.director \
            -repository "${kotlinRepo}" \
            -installIU "${kotlinIU}"; then
          echo "${name}: Kotlin plugin install failed; launching without it (install manually via Marketplace if needed)" >&2
        fi
        ${pkgs.coreutils}/bin/touch "$dataDir.tmp/.ready"
        ${pkgs.coreutils}/bin/rm -rf "$dataDir"
        ${pkgs.coreutils}/bin/mv "$dataDir.tmp" "$dataDir"
      fi
      exec "$dataDir/${exe}" "$@"
    '';

  # Helper to expose official Eclipse distributions with distinct commands & desktop files
  mkEclipseWrapper = { pkg, binName, desktopName, defaultAlias ? false }:
    pkgs.runCommand binName {
      nativeBuildInputs = [ pkgs.makeWrapper ];
    } ''
      mkdir -p $out/bin $out/share/applications $out/share/pixmaps

      # -pluginCustomization seeds plugin preference defaults on first run,
      # deployed to that path by the invoker from config/eclipse/ (see
      # Linker.OVERRIDES). -configuration
      # points Equinox at a writable per-user config area instead of the
      # read-only Nix store install, since Eclipse needs to write there on
      # every launch. The \$HOME escapes are deliberate: they must reach the
      # generated wrapper script as the literal text "$HOME", to be expanded
      # when the user launches Eclipse, not substituted now with the build
      # sandbox's HOME.
      makeWrapper ${mkPluginInstalledBinary { pkgRoot = pkg; exe = "bin/eclipse"; name = binName; }} $out/bin/${binName} \
        --prefix PATH : ${jdk}/bin \
        --add-flags "-vm ${jdk}/bin/java" \
        --add-flags "-pluginCustomization \$HOME/.config/eclipse/plugin_customization.ini" \
        --add-flags "-configuration \$HOME/.local/share/eclipse/configuration"

      ${lib.optionalString defaultAlias ''
        ln -s $out/bin/${binName} $out/bin/eclipse
      ''}

      if [ -f ${pkg}/share/pixmaps/eclipse.xpm ]; then
        ln -s ${pkg}/share/pixmaps/eclipse.xpm $out/share/pixmaps/${binName}.xpm
      fi

      cat <<EOF > $out/share/applications/${binName}.desktop
      [Desktop Entry]
      Type=Application
      Name=${desktopName}
      Comment=Official ${desktopName} IDE
      Exec=$out/bin/${binName} %U
      Icon=${binName}
      Terminal=false
      Categories=Development;IDE;Java;
      EOF
    '';

  # 1. Official Eclipse RCP / RAP (PDE, RCP, Tycho, Plugin development)
  eclipseRcp = mkEclipseWrapper {
    pkg = pkgs.eclipses.eclipse-rcp;
    binName = "eclipse-rcp";
    desktopName = "Eclipse RCP/RAP";
  };

  # 2. Official Eclipse Java EE and Web (Enterprise Java and Web Developers 2026-12 M1)
  eclipseJeePkg = pkgs.eclipses.eclipse-jee.overrideAttrs (_old: {
    version = "2026-12-M1";
    src = pkgs.fetchurl {
      url = "https://download.eclipse.org/technology/epp/downloads/release/2026-12/M1/eclipse-jee-2026-12-M1-linux-gtk-x86_64.tar.gz";
      sha256 = "1hsdc3iwrd4921aam6fdc35m6ljswrljwlrj8naf0m20izk5g0vb";
    };
  });

  eclipseJee = mkEclipseWrapper {
    pkg = eclipseJeePkg;
    binName = "eclipse-jee";
    desktopName = "Eclipse Java EE and Web";
    defaultAlias = true;
  };

  # 3. Official Spring Tool Suite (STS) IDE standalone release
  #
  # Split into an "unpacked" derivation (just the extracted + patched
  # install, so it has its own stable store path) and a wrapper derivation,
  # mirroring the eclipse-rcp/eclipse-jee + mkEclipseWrapper split above --
  # mkPluginInstalledBinary needs a real store path to mirror, not a
  # same-derivation $out that doesn't exist yet at eval time.
  stsUnpacked =
    let
      version = "5.4.0";
      eVersion = "e4.41.0";
    in
    pkgs.stdenv.mkDerivation {
      pname = "spring-tool-suite-unpacked";
      inherit version;

      src = pkgs.fetchurl {
        url = "https://cdn.spring.io/spring-tools/release/dist/${version}.RELEASE/e4.41/spring-tools-for-eclipse-${version}.RELEASE-${eVersion}-linux.gtk.x86_64.tar.gz";
        sha256 = "1k2mpcbpqh002byqr419z7pjdsd9ac7pp93dwbdqa98kfcsfvn8h";
      };

      nativeBuildInputs = [ pkgs.perl ];
      buildInputs = [
        pkgs.fontconfig
        pkgs.freetype
        pkgs.glib
        pkgs.gsettings-desktop-schemas
        pkgs.gtk3
        jdk
        pkgs.libx11
        pkgs.libxrender
        pkgs.libxtst
        pkgs.libsecret
        pkgs.zlib
        pkgs.webkitgtk_4_1
      ];

      buildCommand = ''
        mkdir -p unpack
        tar -xzf $src -C unpack
        mkdir -p $out/lib/sts
        cp -a unpack/sts-*/* $out/lib/sts/

        interpreter="$(cat $NIX_BINTOOLS/nix-support/dynamic-linker)"
        patchelf --set-interpreter "$interpreter" $out/lib/sts/SpringToolsForEclipse

        libCairo=$out/lib/sts/libcairo-swt.so
        if [ -f "$libCairo" ]; then
          patchelf --set-rpath ${lib.makeLibraryPath [
            pkgs.freetype
            pkgs.fontconfig
            pkgs.libx11
            pkgs.libxrender
            pkgs.zlib
          ]} "$libCairo"
        fi

        # Remove bundled justj JVM in ini if present to use system JDK
        perl -i -p0e 's|-vm\nplugins/org.eclipse.justj.*/jre/bin.*\n||' $out/lib/sts/SpringToolsForEclipse.ini
      '';
    };

  springToolSuite =
    pkgs.runCommand "spring-tool-suite" {
      nativeBuildInputs = [ pkgs.makeWrapper ];
    } ''
      mkdir -p $out/bin $out/share/applications $out/share/pixmaps

      makeWrapper ${mkPluginInstalledBinary { pkgRoot = stsUnpacked; exe = "lib/sts/SpringToolsForEclipse"; name = "sts"; }} $out/bin/sts \
        --prefix PATH : ${jdk}/bin \
        --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath [
          pkgs.glib
          pkgs.gtk3
          pkgs.libxtst
          pkgs.libsecret
          pkgs.webkitgtk_4_1
          pkgs.freetype
          pkgs.fontconfig
          pkgs.libx11
          pkgs.libxrender
          pkgs.zlib
        ]} \
        --prefix GIO_EXTRA_MODULES : "${pkgs.glib-networking}/lib/gio/modules" \
        --prefix XDG_DATA_DIRS : "${pkgs.gsettings-desktop-schemas}/share/gsettings-schemas/${pkgs.gsettings-desktop-schemas.name}:$XDG_DATA_DIRS"

      ln -s $out/bin/sts $out/bin/spring-tool-suite
      ln -s ${stsUnpacked}/lib/sts/icon.xpm $out/share/pixmaps/sts.xpm

      cat <<EOF > $out/share/applications/sts.desktop
      [Desktop Entry]
      Type=Application
      Name=Spring Tool Suite
      Comment=Official Spring Tool Suite IDE
      Exec=$out/bin/sts %U
      Icon=sts
      Terminal=false
      Categories=Development;IDE;Java;
      EOF
    '';

  # Common JVM & build tooling
  buildTools = [
    jdk
    (pkgs.gradle.override { java = jdk; })
    (pkgs.maven.override { jdk_headless = jdk; })
    (pkgs.sbt.override { jre = jdk; })
    pkgs.lombok
    pkgs.tomcat10
  ];
in
{
  environment.systemPackages = [
    eclipseRcp
    eclipseJee
    springToolSuite
  ] ++ buildTools;
}
