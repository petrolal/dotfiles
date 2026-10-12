{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    gradle
    jdk21
    graalvmPackages.graalvm-ce
    git
    wayland
    wayland-protocols
    libglvnd
  ];

  # Gradle 8.14's bundled Kotlin DSL compiler can't parse GraalVM's "25.0.2"
  # java.version string (JavaVersion.parse chokes on it), so the Gradle
  # *launcher* must run on a boring JDK -- JAVA_HOME wins over whichever
  # `java` graalvm-ce put on PATH. build.gradle.kts forks actual compilation
  # and native-image into GRAALVM_HOME regardless, so this only affects which
  # JVM runs Gradle itself.
  JAVA_HOME = "${pkgs.jdk21}";
  GRAALVM_HOME = "${pkgs.graalvmPackages.graalvm-ce}";

  # So the FFM SymbolLookup calls in WaylandCanvas (libwayland-client.so,
  # libEGL.so, libGL.so) resolve during native-image's build-time class init.
  LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [ pkgs.wayland pkgs.libglvnd ];

  shellHook = ''
    echo "⛧ [Infernal Mainframe Shell Loaded]"
    echo "Commands available: ./gradlew, ./gradlew nix-switch, native-image"
  '';
}