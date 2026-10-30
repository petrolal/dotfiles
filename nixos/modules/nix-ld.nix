# nix-ld lets unpatched/FHS dynamic binaries find their dynamic linker and
# libraries without polluting every process on the system via a global
# LD_LIBRARY_PATH. Centralized here rather than split across packages.nix
# and jetbrains.nix (where it previously lived as two separate
# `programs.nix-ld.libraries` declarations, merged by the module system into
# one list with `libx11` duplicated) since it's a single host-wide concern,
# not scoped to any one tool.
{ pkgs, ... }:
{
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = with pkgs; [
    # Generic X11/GL, for unpatched AppImages (packages.nix's appimage-run).
    libx11
    libGL

    # JetBrains Toolbox and the unpatched JetBrains IDE binaries it manages
    # (both Toolbox-downloaded IDEs in ~/.local/share/JetBrains/Toolbox and
    # IDE instances Gradle's `org.jetbrains.intellij.platform` plugin fetches
    # into ~/.gradle/caches for `./gradlew runIde`) bundle a prebuilt
    # JetBrains Runtime (JBR), JCEF, and native helpers that expect these FHS
    # paths. nix-ld provides the dynamic linker and libraries for those
    # binaries when launched directly (Toolbox-generated .desktop files, CLI
    # scripts, or Gradle tasks).
    libxext
    libxrender
    libxtst
    libxi
    libxrandr
    libxcursor
    libxfixes
    libxcomposite
    libxdamage
    libxcb
    libxxf86vm
    libxkbcommon
    wayland
    freetype
    fontconfig
    expat
    zlib
    stdenv.cc.cc.lib
    glib
    gtk3
    cairo
    pango
    atk
    at-spi2-core
    at-spi2-atk
    dbus
    libdrm
    libgbm
    mesa
    alsa-lib
    cups
    nspr
    nss
    udev
    e2fsprogs
  ];
}
