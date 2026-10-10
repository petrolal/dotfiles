{ pkgs, lib, ... }:

let
  jdk21 = pkgs.jdk21;
  jdk17 = pkgs.jdk17;

  # Helper to expose official Eclipse distributions with distinct commands & desktop files
  mkEclipseWrapper = { pkg, binName, desktopName, defaultAlias ? false }:
    pkgs.runCommand binName {
      nativeBuildInputs = [ pkgs.makeWrapper ];
    } ''
      mkdir -p $out/bin $out/share/applications $out/share/pixmaps

      # -pluginCustomization seeds the native Emacs key scheme
      # (org.eclipse.ui.emacsAcceleratorConfiguration: C-x C-f, C-x b, C-x 2/3,
      # etc.) as the default on first run, deployed to that path by the
      # invoker from config/eclipse/ (see Linker.OVERRIDES). -configuration
      # points Equinox at a writable per-user config area instead of the
      # read-only Nix store install, since Eclipse needs to write there on
      # every launch. The \$HOME escapes are deliberate: they must reach the
      # generated wrapper script as the literal text "$HOME", to be expanded
      # when the user launches Eclipse, not substituted now with the build
      # sandbox's HOME.
      makeWrapper ${pkg}/bin/eclipse $out/bin/${binName} \
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
    defaultAlias = true;
  };

  # 2. Official Eclipse Java EE (Enterprise Java, Jakarta EE, WTP)
  eclipseJee = mkEclipseWrapper {
    pkg = pkgs.eclipses.eclipse-jee;
    binName = "eclipse-jee";
    desktopName = "Eclipse Java EE";
  };

  # 3. Official Spring Tool Suite (STS) IDE standalone release
  springToolSuite =
    let
      version = "5.4.0";
      eVersion = "e4.41.0";
    in
    pkgs.stdenv.mkDerivation {
      pname = "spring-tool-suite";
      inherit version;

      src = pkgs.fetchurl {
        url = "https://cdn.spring.io/spring-tools/release/dist/${version}.RELEASE/e4.41/spring-tools-for-eclipse-${version}.RELEASE-${eVersion}-linux.gtk.x86_64.tar.gz";
        sha256 = "1k2mpcbpqh002byqr419z7pjdsd9ac7pp93dwbdqa98kfcsfvn8h";
      };

      nativeBuildInputs = [ pkgs.makeWrapper pkgs.perl ];
      buildInputs = [
        pkgs.fontconfig
        pkgs.freetype
        pkgs.glib
        pkgs.gsettings-desktop-schemas
        pkgs.gtk3
        jdk21
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

        mkdir -p $out/bin $out/share/applications $out/share/pixmaps

        makeWrapper $out/lib/sts/SpringToolsForEclipse $out/bin/sts \
          --prefix PATH : ${jdk21}/bin \
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
        ln -s $out/lib/sts/icon.xpm $out/share/pixmaps/sts.xpm

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
    };

  # Common JVM & build tooling
  buildTools = [
    jdk21
    jdk17
    (pkgs.gradle.override { java = jdk21; })
    (pkgs.maven.override { jdk_headless = jdk21; })
    (pkgs.sbt.override { jre = jdk21; })
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
