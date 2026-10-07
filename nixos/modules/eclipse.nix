{ pkgs, ... }:

# Eclipse IDE for JVM development (RCP/PDE/Tycho + JDT), configured the Nix
# way: package choice and eclipse.ini VM args are declared here instead of
# hand-edited in the installed IDE.
#
# Scope notes (checked against nixpkgs before writing this):
#   - eclipse-rcp (not eclipse-jee) is used because it bundles PDE, needed for
#     RCP plugin / Tycho work. JDT and EGit (Git) ship in box in both
#     editions, so neither needs an extra plugin.
#   - eclipses.plugins.scala was removed from nixpkgs upstream (deprecated),
#     and there are no nixpkgs packages for Spring Tools 4 (STS4), Kotlin for
#     Eclipse, Groovy Development Tools, or Quarkus/Micronaut tooling -- none
#     of those exist as Nix derivations to pull in declaratively. Provisioning
#     them would mean an imperative p2-director bootstrap outside of Nix's
#     purity model, so they're left out here.
#   - Spring Boot/Micronaut/Quarkus and Scala/Kotlin/Groovy project support
#     comes from the build tools below (Gradle/Maven/sbt) plus Eclipse's
#     built-in Buildship/m2e, the same as any project built via
#     nixos/templates/jvm.
let
  jdk21 = pkgs.jdk21;
  jdk17 = pkgs.jdk17;

  eclipseIde = pkgs.eclipses.eclipseWithPlugins {
    eclipse = pkgs.eclipses.eclipse-rcp;

    # eclipses.plugins currently offers: cdt, checkstyle, eclemma, findbugs,
    # spotbugs, testng, jdt-codemining, jsonedit, and a few others. Add from
    # that set as needed, e.g.:
    # plugins = with pkgs.eclipses.plugins; [ checkstyle eclemma ];
    plugins = [ ];

    # Appended to eclipse.ini's existing -vmargs section (not a replacement).
    jvmArgs = [
      "-Xms512m"
      "-Xmx4g"
      "--add-opens=java.base/java.lang=ALL-UNNAMED"
      "--add-opens=java.base/java.util=ALL-UNNAMED"
      "-javaagent:${pkgs.lombok}/share/java/lombok.jar"
    ];
  };

  # JDK21 is the default toolchain; JDK17 is kept on PATH so it can be
  # registered under Eclipse's Installed JREs for projects pinned to it.
  buildTools = [
    jdk21
    jdk17
    (pkgs.gradle.override { java = jdk21; })
    (pkgs.maven.override { jdk_headless = jdk21; })
    (pkgs.sbt.override { jre = jdk21; })
    pkgs.lombok
  ];
in
{
  environment.systemPackages = [ eclipseIde ] ++ buildTools;
}
