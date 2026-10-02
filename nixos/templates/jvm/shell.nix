# shell.nix --- Per-project JVM toolchain (the Nix replacement for SDKMAN!)
#
# Enter with `nix-shell`, or automatically via direnv (`use nix` in .envrc).
# Switch the JDK without editing this file:
#   nix-shell --argstr java 17
# Available versions: 8, 11, 17, 21, 25 (anything nixpkgs ships as jdkNN).

{ pkgs ? import <nixpkgs> { }
, java ? "21"
}:

let
  jdk = pkgs."jdk${java}";
in
pkgs.mkShell {
  packages = [
    jdk
    # Build tools are rebuilt against the selected JDK.
    (pkgs.gradle.override { java = jdk; })
    (pkgs.maven.override { jdk_headless = jdk; })

    # Uncomment what the project needs:
    # (pkgs.kotlin.override { jre = jdk; })
    # (pkgs.scala.override { jre = jdk; })
    # (pkgs.sbt.override { jre = jdk; })
  ];

  JAVA_HOME = jdk.home;

  shellHook = ''
    echo "☕ JDK ${jdk.version} loaded (JAVA_HOME=$JAVA_HOME)"
  '';
}
