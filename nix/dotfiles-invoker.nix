# dotfiles-invoker --- hermetic GraalVM native-image build of the Scala CLI.
#
# Deliberately bypasses sbt: src/main has zero external dependencies beyond
# scala3-library (bundled in pkgs.scala_3) and GraalVM 25 JDK.
{
  lib,
  stdenv,
  scala_3,
  graalvmPackages,
}:
let
  scala = scala_3.override { jre = graalvmPackages.graalvm-ce; };
in
stdenv.mkDerivation {
  pname = "dotfiles-invoker";
  version = "1.3.0";

  src = ../src/main;

  nativeBuildInputs = [
    scala
    graalvmPackages.graalvm-ce
  ];

  dontConfigure = true;

  buildPhase = ''
    runHook preBuild

    mkdir -p classes
    find scala -name '*.scala' > sources.txt
    scalac -d classes @sources.txt

    # META-INF/native-image/.../reachability-metadata.json has to be on the
    # classpath for native-image's auto-registration to see it.
    cp -r resources/. classes/

    SCALA_BIN=$(type -p scalac)
    SCALA_DIR=$(dirname $(dirname $(readlink -f "$SCALA_BIN")))
    SCALA_LIBS=$(find "$SCALA_DIR" -name "scala3-library_3*.jar" -o -name "scala-library*.jar" | tr '\n' ':')

    native-image \
      -cp "classes:$SCALA_LIBS" \
      -o invoker \
      --gc=serial \
      --enable-native-access=ALL-UNNAMED \
      -O3 \
      dev.petrolal.dotfiles.cli.Cli

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    install -Dm755 invoker $out/bin/invoker

    # Mirrors the exact relative layout DotfilePaths.findDotfilesRoot() /
    # Wallpaper.resolveShader() already expect (exe-parent "bin" stripped,
    # then src/main/resources/static/<name>) -- so `invoker wallpaper` finds
    # its shaders with no env var or code change needed, while `deploy`/
    # `uninstall`/`generate` still require DOTFILES_DIR pointed at an actual
    # dotfiles checkout, since those operate on a live, editable repo rather
    # than anything this package can sensibly bundle.
    install -Dm644 resources/shaders/wallpaper.vert \
      $out/src/main/resources/shaders/wallpaper.vert
    install -Dm644 resources/shaders/wallpaper.frag \
      $out/src/main/resources/shaders/wallpaper.frag
    install -Dm644 resources/shaders/blit.frag \
      $out/src/main/resources/shaders/blit.frag

    runHook postInstall
  '';

  meta = {
    description = "Dotfiles symlinker, XFCE settings applier, and OpenGL wallpaper daemon";
    mainProgram = "invoker";
    platforms = [ "x86_64-linux" ];
    license = lib.licenses.gpl3Plus;
  };
}
