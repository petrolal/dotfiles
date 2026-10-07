{ pkgs }:

# Spring Tools 5 (formerly "Spring Tools 4"/STS4), packaged as an Eclipse
# dropin. Unlike dotfiles-syntax (pure declarative XML/JSON, buildable with
# no network), STS5 is a large, actively-developed plugin suite shipped only
# as a p2 update site -- there's no way to hand-write an equivalent. This
# fetches it the way Nix fetches any external binary artifact: a fixed-output
# derivation (FOD). The p2 director genuinely needs network access to resolve
# and download from cdn.spring.io during the build, which a FOD is explicitly
# allowed (same sanction fetchurl gets) because the result is verified
# against outputHash afterward -- this is NOT the same as an un-pinned
# imperative p2 install on the live system; it's deterministic and
# reproducible exactly like any other Nix-fetched dependency.
#
# How it works (see install.sh): runs the real Eclipse p2 director inside a
# writable scratch copy of eclipse-rcp itself (not an empty profile), so
# dependencies eclipse-rcp already ships (e.g. com.google.guava, needed by
# the XML namespaces feature but not re-published by the STS5 repo itself)
# resolve against what's already there instead of needing a second repo for
# every transitive dep. A secondary -repository (the 2026-09 Eclipse release
# train aggregate) covers anything genuinely missing. Installed IUs are then
# diffed against a pre-install snapshot, and only the newly added
# plugins/features are copied out -- so the dropin doesn't duplicate
# anything eclipse-rcp already has.
#
# Scope: the Spring-specific feature groups from the STS5 e4.41 update site
# (Boot support/editors, Boot Dashboard, Spring XML namespaces, and the Boot/
# Bosh/Cloud&nbsp;Foundry/Concourse language servers) -- "all Spring Tools
# support". Deliberately excludes the cosmetic standalone-product branding
# feature, and doesn't re-pull platform/jdt/pde/egit/buildship/m2e/
# wildwebdeveloper/docker features also present in that composite repo:
# eclipse-rcp already ships those at matching versions.
pkgs.stdenv.mkDerivation {
  pname = "eclipse-plugin-spring-tools";
  version = "5.4.0";

  src = ./install.sh;
  dontUnpack = true;

  # install.sh invokes the Equinox launcher JAR directly (plain `java -jar`)
  # rather than eclipse-rcp's own bin/eclipse launcher or native eclipse
  # binary -- that native launcher's GTK-linked launcher library hangs
  # indefinitely pre-JVM in this sandbox (found by elimination: three
  # separate fixes below got progressively further without ever actually
  # solving it, and bypassing the native launcher entirely did). Trying to
  # reproduce the headless path through the real launcher still needs Xvfb
  # (SWT/GTK otherwise fails fast with "Cannot open display") and a
  # fontconfig config to get that far, so both stay even though the actual
  # p2.director run no longer goes through that launcher.
  nativeBuildInputs = [ pkgs.findutils pkgs.coreutils pkgs.xvfb-run ];
  FONTCONFIG_FILE = pkgs.makeFontsConf { fontDirectories = [ ]; };
  # Belt-and-suspenders: the sandbox has no /etc/ssl/certs, and while this
  # wasn't the actual cause of the hang above (bypassing the native launcher
  # fixed that on its own), JGit (pulled in by EGit/m2e, confirmed active in
  # the build log) can use a system HTTP transport that depends on this.
  SSL_CERT_FILE = "${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt";
  GIT_SSL_CAINFO = "${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt";

  buildPhase = ''
    runHook preBuild
    xvfb-run -a sh "$src" "${pkgs.eclipses.eclipse-rcp}" "${pkgs.jdk25}" "$out"
    runHook postBuild
  '';

  dontInstall = true;

  outputHashMode = "recursive";
  outputHashAlgo = "sha256";
  outputHash = "sha256-Q7kfwfucZ55Oy+QcB0h8dz7fYHX/EtAqmzRKGCN/wwk=";

  meta = {
    description = "Spring Tools 5 (Spring Boot support, Boot Dashboard, Spring XML namespaces, Boot/Bosh/CF/Concourse language servers) for Eclipse";
    license = pkgs.lib.licenses.epl20;
  };
}
