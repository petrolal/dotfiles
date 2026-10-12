{ pkgs, ... }:

let
  # Native deps for the Wayland layer-shell OpenGL wallpaper renderer
  # (java.lang.foreign bindings in src/main/java/.../wallpaper): a hand-rolled
  # wl_display/zwlr_layer_shell_v1 client plus EGL/GL, no GLFW/LWJGL (neither
  # exposes the layer-shell protocol).
  dynamicLibs = with pkgs; [
    wayland
    libglvnd # libEGL + libGL dispatch
  ];

  buildTools = with pkgs; [
    gnumake
    git
    jdk21
    graalvmPackages.graalvm-ce
    wayland-protocols
    wayland-scanner
  ] ++ dynamicLibs;

  wallpaperTools = with pkgs; [
    mpv
  ];

  devTools = with pkgs; [
    emacs
    claude-code
  ];

  # Fast search, used by Hell Emacs' consult-ripgrep/consult-find (C-c s p / C-c s f).
  searchTools = with pkgs; [
    ripgrep
    fd
  ];

  # Language servers, linters and CLIs that Hell Emacs' modules (:lang glsl,
  # :lang nix, :lang sh, :lang terraform, :lang sql, :lang protobuf, :lang org)
  # shell out to but don't install themselves.
  languageTools = with pkgs; [
    nixd            # Nix language server
    glslls          # GLSL language server
    glslang         # glslangValidator
    shaderc         # glslc
    shellcheck      # shell script diagnostics
    terraform
    terraform-ls
    protobuf        # protoc
    postgresql      # psql client
    pandoc          # extended Org exports
  ];

  # The Scala language server. Installed here (reproducibly, pinned to
  # nixpkgs) rather than left to lsp-metals, which would otherwise
  # self-install it via coursier on first use.
  scalaTools = with pkgs; [
    metals
  ];

  systemTools = with pkgs; [
    pciutils
    htop
    fastfetch
    wl-clipboard
    curl
    wget
    zip
    unzip
    which
    less
    rlwrap
  ];

  mediaUtils = with pkgs; [
    shotcut
    appimage-run
    nomacs      # image viewer
    celluloid   # GTK frontend for mpv, used as the default video viewer
    spotify
  ];

  # General-purpose apps reached for daily, outside any dev/media workflow.
  dailyTools = with pkgs; [
    galculator
    xarchiver   # archive manager (zip/tar/7z)
    evince      # PDF viewer
  ];

in {
  environment.systemPackages =
    buildTools
    ++ wallpaperTools
    ++ devTools
    ++ searchTools
    ++ languageTools
    ++ scalaTools
    ++ systemTools
    ++ mediaUtils
    ++ dailyTools;

  # Lets unpatched/FHS binaries (AppImages, etc.) find dynamic libs without
  # polluting every process on the system via a global LD_LIBRARY_PATH.
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = dynamicLibs;

  # Gradle 8.14's bundled Kotlin DSL compiler can't parse GraalVM's "25.0.2"
  # java.version string, so whatever runs `./gradlew` itself (bootstrap.sh,
  # an interactive shell) must default to a boring JDK. build.gradle.kts
  # forks actual compilation and native-image into GRAALVM_HOME regardless of
  # which JVM launched Gradle, so this only changes Gradle's own runtime.
  environment.variables.JAVA_HOME = "${pkgs.jdk21}";
  environment.variables.GRAALVM_HOME = "${pkgs.graalvmPackages.graalvm-ce}";
}
