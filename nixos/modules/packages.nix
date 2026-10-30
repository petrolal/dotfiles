{ pkgs, ... }:

let
  # libx11/libGL also appear in nix-ld.nix's `programs.nix-ld.libraries`
  # (nix-ld needs its own copy of the option's value, so that overlap is
  # inherent, not a leftover to merge away) -- here they're genuine
  # environment.systemPackages entries appimage-run and other unpatched
  # binaries can dlopen directly.
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
    bruno
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

    # core/devops.lua shells out to these directly -- not Mason-installable
    # (they're plain CLIs, not LSPs/linters mason-registry packages).
    tflint       # <leader>otl terraform lint
    trivy        # <leader>ots terraform security scan (preferred over tfsec)
    awscli2      # cloudformation validate fallback when cfn-lint is absent
    aws-sam-cli  # <leader>oc* sam build/local invoke/start-api
    lazydocker   # <leader>od docker dashboard / editor-snacks.lua terminal

    # jq: health/clients.lua HTTP client tool (<leader>ahj response filtering)
    jq

    # bootstrap.sh currently npm/go-installs these on first run; declaring
    # them here means a fresh machine has them without that imperative step.
    stylua             # Lua formatter (tools-formatting.lua), npm fallback in bootstrap.sh
    yamlfmt            # YAML formatter (conform.nvim), go-installed in bootstrap.sh
    mermaid-cli         # `mmdc`, Snacks.image Mermaid rendering, npm-installed in bootstrap.sh
    sonar-scanner-cli   # `sonar-scanner`, <leader>xsp whole-codebase scan, npm-installed in bootstrap.sh
  ];

  # Runtimes mason-tool-installer.nvim's own installers shell out to.
  # development.nix deliberately keeps node/python/go out of
  # environment.systemPackages (per-project only, via direnv) -- but Mason
  # isn't a project, it's the editor's own package manager, so most of
  # lua/TetraVim/plugins/tools-mason.lua's ensure_installed list (npm
  # installs for typescript-language-server, eslint-lsp, the Vue/Svelte/
  # Astro/Angular/html/css/json/yaml servers, prettier, tree-sitter-cli,
  # ...; pip/venv installs for basedpyright, ruff, ansible-lint, cfn-lint,
  # yamllint, djlint) silently no-ops without these on $PATH -- bootstrap.sh
  # prints "node/python3/go not found -- skipping ..." and moves on instead
  # of failing loudly. go covers bootstrap.sh's `go install` fallbacks
  # (yamlfmt, shfmt, grpcurl/osv-scanner before nvimTools above wins on
  # $PATH anyway); most other Go/Rust Mason packages fetch prebuilt release
  # binaries and don't need a local toolchain.
  nvimRuntimes = with pkgs; [
    nodejs_22 # includes npm/npx
    python3   # includes pip/venv
    go
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
    google-cloud-sdk # gcloud/gsutil/bq CLIs
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
    ++ nvimRuntimes
    ++ languageTools
    ++ systemTools
    ++ mediaUtils
    ++ dailyTools;
}
