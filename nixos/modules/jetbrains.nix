# IntelliJ IDEA Community replaces the previous Eclipse IDE / Spring Tool
# Suite setup entirely (see git history for the old mkEclipseWrapper-based
# eclipse.nix, which this file replaces one-for-one).
#
# jetbrains.idea-community was discontinued by JetBrains in 2025 and removed
# from nixpkgs; jetbrains.idea-oss is nixpkgs' current free/open-source
# equivalent, built from the same IntelliJ Community sources (see
# modules/hardware.nix for the required permittedInsecurePackages entry --
# nixpkgs no longer updates this package, so it carries known CVEs).
{ pkgs, ... }:

let
  # vmopts can't be overridden through pkgs.jetbrains.idea-oss / .override
  # (neither forwards the args mkJetBrainsProduct actually accepts), so the
  # bundled idea64.vmoptions is appended to in place instead -- this keeps
  # the Nix-specific lines the real build already put there (jna.library.path
  # etc.), rather than replacing the whole file and silently losing them.
  #
  # $out/share/applications is a *symlink* into a separate, already-built
  # makeDesktopItem derivation (verified: `readlink`/`stat` on a built
  # package shows it pointing at .../intellij-idea-oss.desktop/share/...),
  # so substituteInPlace/cp on the .desktop file inside it fails with
  # "Operation not permitted" -- it belongs to a different, immutable store
  # path, not this one. Read it (reads work, /nix/store is world-readable),
  # then replace the whole symlink with a real directory of our own.
  idea = pkgs.jetbrains.idea-oss.overrideAttrs (old: {
    postFixup = (old.postFixup or "") + ''
            sed 's/^Categories=Development$/Categories=Development;IDE;Java;/' \
              "$out/share/applications/intellij-idea-oss.desktop" > "$TMPDIR/intellij-idea-oss.desktop"
            rm "$out/share/applications"
            mkdir -p "$out/share/applications"
            install -m 444 "$TMPDIR/intellij-idea-oss.desktop" "$out/share/applications/intellij-idea-oss.desktop"

            cat >> $out/intellij-idea-oss/bin/idea64.vmoptions <<'VMOPTS'
      -Dawt.useSystemAAFontSettings=on
      -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine
      VMOPTS
    '';
  });
in
{
  environment.systemPackages = [
    idea
    pkgs.jdk21
  ];

  # Pinned globally per explicit request, as the default JDK for IntelliJ
  # project SDKs. This sits alongside, not instead of, modules/development.nix's
  # per-project `jvm-init <version>` direnv flow -- if a project needs a
  # different default `java` on PATH than this, pin that project's own
  # shell.nix rather than changing this.
  environment.variables.JAVA_HOME = "${pkgs.jdk21}/lib/openjdk";

  # `idea` above is the nixpkgs-wrapped IDE and already works fine. This is
  # for the *other* IntelliJ binary every Gradle project using the
  # `org.jetbrains.intellij.platform` plugin pulls in: `./gradlew runIde`
  # downloads its own copy of the IDE straight from JetBrains' CDN into
  # ~/.gradle/caches, as a plain unpatched Linux tarball, and launches it
  # unmodified. That binary's bundled JetBrains Runtime (JBR) expects FHS
  # paths (libXext.so.6 etc. in a plain /usr/lib), which NixOS doesn't have,
  # so it fails with UnsatisfiedLinkError before any window appears.
  #
  # nix-ld is the general-purpose fix for exactly this class of problem --
  # any unpatched prebuilt binary that expects an FHS layout -- so it covers
  # every current and future Gradle intellij.platform project, not just one.
  # Per-project `shell.nix`/LD_LIBRARY_PATH workarounds remain valid as a
  # portable fallback for contributors on non-NixOS-with-nix-ld machines, but
  # shouldn't be necessary here once this is active.
  programs.nix-ld.enable = true;
  programs.nix-ld.libraries = with pkgs; [
    libx11
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
    cairo
    pango
    atk
    at-spi2-core
    at-spi2-atk
    dbus
    mesa
    alsa-lib
    cups
    nspr
    nss
  ];
}
