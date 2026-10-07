{ pkgs }:

# A small from-scratch Eclipse bundle (no code, pure declarative extensions)
# that teaches the already-bundled TM4E/Generic Editor framework new file
# types: Nix, GTK rc, and GLSL (OpenGL/WebGL shaders) get their own TextMate
# grammars; GtkBuilder .ui/.glade reuse the platform's existing XML grammar.
# Built entirely from source in the sandbox -- no network fetch, no p2
# update-site install, matching every other package in this directory.
#
# Shaped like nixpkgs' own eclipses.plugins outputs ($out/eclipse/dropins/
# <name>/plugins/<bundle>.jar) so it drops straight into eclipseWithPlugins'
# `plugins` list alongside e.g. cdt.
pkgs.stdenv.mkDerivation {
  pname = "eclipse-plugin-dotfiles-syntax";
  version = "1.0.0";
  src = ./.;

  nativeBuildInputs = [ pkgs.zip ];
  dontUnpack = true;
  dontBuild = false;

  buildPhase = ''
    runHook preBuild
    jarDir=$TMPDIR/dotfiles.syntax_1.0.0
    mkdir -p "$jarDir"
    cp -r "$src"/META-INF "$src"/plugin.xml "$src"/grammars "$src"/languageConfigurations "$jarDir"/
    ( cd "$jarDir" && zip -q -r -X ../dotfiles.syntax_1.0.0.jar . )
    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall
    dropinDir=$out/eclipse/dropins/dotfiles-syntax/plugins
    mkdir -p "$dropinDir"
    cp "$TMPDIR"/dotfiles.syntax_1.0.0.jar "$dropinDir"/
    runHook postInstall
  '';

  # eclipseWithPlugins's pluginEnv (pkgs/applications/editors/eclipse/default.nix)
  # filters its `plugins` list down to `lib.filter (x: x ? isEclipsePlugin)` --
  # without this, this derivation is silently dropped from the merged dropins,
  # no error, nothing in the build log. nixpkgs' own buildEclipsePluginBase sets
  # this via passthru; same here.
  passthru.isEclipsePlugin = true;

  meta = {
    description = "Nix/GTK-rc/GLSL syntax highlighting for Eclipse via TM4E";
    license = pkgs.lib.licenses.gpl3Plus;
  };
}
