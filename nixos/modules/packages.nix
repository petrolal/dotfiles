{ pkgs, ... }:

let
  dynamicLibs = with pkgs; [
    libx11
    libGL
  ];

  buildTools = with pkgs; [
    gnumake
    cmake
    pkg-config
    git
    gcc  # C compiler for Tree-sitter parsers & telescope-fzf-native (TetraVim/nvim)
    graalvmPackages.graalvm-ce
  ] ++ dynamicLibs;

  wallpaperTools = with pkgs; [
    mpv
    xwinwrap
    feh
    mangohud  # FPS/frametime/GPU overlay for testing the OpenGL wallpaper's performance
  ];

  devTools = with pkgs; [
    emacs
    neovim
    claude-code
    claude-desktop-fhs
    bruno
    kiro
  ];

  # Fast search, used by Hell Emacs' consult-ripgrep/consult-find (C-c s p / C-c s f)
  # and by TetraVim's (~/TetraVim, symlinked to ~/.config/nvim) Telescope/Snacks
  # picker and live grep.
  searchTools = with pkgs; [
    ripgrep
    fd
  ];

  # TetraVim (~/TetraVim) requirements not already covered above: Neovim itself,
  # Java (jdk21, see jetbrains.nix), ripgrep/fd (searchTools), gcc/make
  # (buildTools), git, docker/kubectl (containers.nix) and Nerd Fonts
  # (fonts.nix) are provided by other modules. These cover the rest.
  nvimTools = with pkgs; [
    lazygit     # Git TUI modal (<leader>gg)
    grpcurl     # gRPC interactive client (<leader>ag)
    grpcui
    osv-scanner # dependency CVE vulnerability audits (<leader>xv)
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
    kitty
    thunar  # standalone GTK file manager; no longer pulled in transitively now that desktopManager.xfce is gone
    grim         # Wayland screenshot capture, bound in config/hypr/hyprland.lua
    slurp        # region selection for grim
  ];

  mediaUtils = with pkgs; [
    shotcut
    appimage-run
    nomacs      # image viewer
    celluloid   # GTK frontend for mpv, used as the default video viewer
    spotify
    playerctl
    gpu-screen-recorder-gtk
  ];

  # General-purpose apps reached for daily, outside any dev/media workflow.
  dailyTools = with pkgs; [
    galculator
    xarchiver   # archive manager (zip/tar/7z)
    evince      # PDF viewer
    telegram-desktop
  ];

in {
  environment.systemPackages =
    buildTools
    ++ wallpaperTools
    ++ devTools
    ++ searchTools
    ++ nvimTools
    ++ languageTools
    ++ systemTools
    ++ mediaUtils
    ++ dailyTools;

  # Lets unpatched/FHS binaries (AppImages, etc.) find dynamic libs without
  # polluting every process on the system via a global LD_LIBRARY_PATH.
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = dynamicLibs;
}
