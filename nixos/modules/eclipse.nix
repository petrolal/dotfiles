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
#   - Builds are Tycho-only. eclipse-rcp bundles PDE's UI (manifest/plugin.xml
#     /product editors, launch configs) -- required for RCP/RAP editing and
#     kept. What's NOT used is PDE Build, the deprecated Ant-based headless
#     build system Tycho replaced: nothing here invokes it (no
#     `-application org.eclipse.pde.build.*`), and no project should add an
#     Eclipse "Export > Deployable plug-ins" / PDE Build step -- build via
#     `mvn` (Tycho, from pom.xml) instead, same as inferna-ide's releng/
#     reactor. nixpkgs ships eclipse-rcp as one pre-built tarball with no
#     granular feature selection, so PDE Build's jars can't be stripped from
#     the install itself; this is a usage policy, not a package option.
#   - Java EE/Jakarta EE backend support is build-tool level only, for the
#     same reason: no nixpkgs derivation for Eclipse WTP. Jakarta EE API
#     jars (servlet, JPA, CDI, ...) aren't installed here at all -- they're
#     ordinary per-project Maven/Gradle dependencies, resolved the same way
#     Spring Boot's are. tomcat10 below is just a standalone servlet
#     container to deploy/test a WAR against outside the IDE. None of this
#     touches the eclipse-rcp package or its plugin set, so it can't
#     conflict with RCP/RAP/PDE/Tycho tooling.
let
  jdk21 = pkgs.jdk21;
  jdk17 = pkgs.jdk17;

  # Custom TextMate-grammar bundle (source in ./eclipse-plugins/dotfiles-syntax)
  # giving the Generic Editor + TM4E -- both already shipped in eclipse-rcp --
  # syntax highlighting for file types the bundled language pack doesn't cover:
  # Nix, GTK rc files, and GLSL (OpenGL/WebGL shaders). .ui/.glade reuse the
  # platform's existing XML grammar rather than shipping a new one. Built
  # entirely from source in the sandbox (zip into a plain OSGi bundle jar
  # dropped into eclipse/dropins), so it needs no network fetch and no
  # imperative p2 install -- unlike STS4/Groovy-Eclipse/etc noted above, this
  # one *can* be done declaratively because it's pure plugin.xml/JSON, no
  # compiled Java.
  dotfilesSyntaxPlugin = import ./eclipse-plugins/dotfiles-syntax { inherit pkgs; };

  eclipseIde = pkgs.eclipses.eclipseWithPlugins {
    eclipse = pkgs.eclipses.eclipse-rcp;

    # eclipses.plugins currently offers: cdt, checkstyle, eclemma, findbugs,
    # spotbugs, testng, jdt-codemining, jsonedit, and a few others.
    #   - cdt: real "good Makefile plugin" -- Makefile editor/targets view/
    #     build integration (note: Makefile *syntax highlighting* alone
    #     already works out of the box, eclipse-rcp's bundled TM4E language
    #     pack recognizes Makefile/GNUmakefile/*.mk/*.mak with no plugin).
    plugins = [ pkgs.eclipses.plugins.cdt dotfilesSyntaxPlugin ];

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

    # Standalone servlet container for Jakarta EE web-app deploy/test outside
    # the IDE (Spring Boot doesn't need this -- it embeds its own Tomcat).
    pkgs.tomcat10
  ];
in
{
  environment.systemPackages = [ eclipseIde ] ++ buildTools;
}
