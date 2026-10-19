# dotfiles-invoker --- hermetic GraalVM native-image build of the Java CLI.
#
# Deliberately bypasses Gradle: src/main has zero external dependencies (only
# the test sourceSet pulls from Maven Central, see build.gradle), so there is
# nothing here that needs network access to resolve. javac + native-image
# straight from the Nix-provided GraalVM toolchain is enough, which keeps
# this a genuinely hermetic, cacheable/substitutable derivation instead of
# needing a vendored Gradle dependency lockfile (gradle2nix) for no reason.
#
# -march=native is intentionally dropped here (unlike the Gradle
# `nativeCompile` task, which targets only the machine running the build):
# a substitutable store path has to run on whatever CPU the consumer has,
# not just the one that built it.
{
  lib,
  stdenv,
  graalvmPackages,
}:

stdenv.mkDerivation {
  pname = "dotfiles-invoker";
  version = "1.2.0";

  src = ../src/main;

  nativeBuildInputs = [ graalvmPackages.graalvm-ce ];

  dontConfigure = true;

  buildPhase = ''
    runHook preBuild

    mkdir -p classes
    find java -name '*.java' > sources.txt
    javac -d classes --release 25 @sources.txt

    # META-INF/native-image/.../reachability-metadata.json has to be on the
    # classpath for native-image's auto-registration to see it.
    cp -r resources/. classes/

    native-image \
      -cp classes \
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
    install -Dm644 resources/static/wallpaper.vert \
      $out/src/main/resources/static/wallpaper.vert
    install -Dm644 resources/static/wallpaper.frag \
      $out/src/main/resources/static/wallpaper.frag

    runHook postInstall
  '';

  meta = {
    description = "Dotfiles symlinker, XFCE settings applier, and OpenGL wallpaper daemon";
    mainProgram = "invoker";
    platforms = [ "x86_64-linux" ];
    license = lib.licenses.gpl3Plus;
  };
}
