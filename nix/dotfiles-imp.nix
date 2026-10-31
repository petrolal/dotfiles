# dotfiles-imp --- hermetic GraalVM native-image build of the Java CLI.
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
  pname = "dotfiles-imp";
  version = "1.3.0";

  src = ../apps/cli/src/main;

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
      -o imp \
      --gc=serial \
      --enable-native-access=ALL-UNNAMED \
      -O3 \
      dev.petrolal.dotfiles.cli.Cli

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    install -Dm755 imp $out/bin/imp

    runHook postInstall
  '';

  meta = {
    description = "Dotfiles symlinker and 3D app-launcher overlay";
    mainProgram = "imp";
    platforms = [ "x86_64-linux" ];
    license = lib.licenses.gpl3Plus;
  };
}
