{ pkgs, ... }:

let
  dynamicLibs = with pkgs; [
    libx11
    libGL
  ];

  buildTools = with pkgs; [
    gnumake
    git
    graalvmPackages.graalvm-ce
  ] ++ dynamicLibs;

  wallpaperTools = with pkgs; [
    mpv
    xwinwrap
    feh
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
    xclip
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
    ++ mediaUtils;

  # Lets unpatched/FHS binaries (AppImages, etc.) find dynamic libs without
  # polluting every process on the system via a global LD_LIBRARY_PATH.
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = dynamicLibs;
}
