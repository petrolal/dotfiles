# Makefile --- Build and deploy the Abyssal Biopunk dotfiles
# License: GPL-3.0-or-later
#
# Follows the GNU Coding Standards, "Makefile Conventions":
#   https://www.gnu.org/prep/standards/html_node/Makefile-Conventions.html
#
# Every variable below can be overridden on the command line, e.g.
#   make deploy DEPLOY_FLAGS=--dry-run
#   make check MVN=/opt/maven/bin/mvn

SHELL = /bin/sh

# Clear built-in suffix rules; nothing here uses them.
.SUFFIXES:

# Remove a half-written target (e.g. a truncated invoker) if its recipe fails.
.DELETE_ON_ERROR:

# ---------------------------------------------------------------------------
# Programs
# ---------------------------------------------------------------------------
JAVA          = java
MVN           = mvn
# The native-image binary ships in a separate GraalVM package (see
# nixos/modules/packages.nix), distinct from the plain OpenJDK `java`/`mvn`
# otherwise on PATH -- native-maven-plugin requires JAVA_HOME to actually BE
# a GraalVM distribution for the `native` profile, so this resolves one from
# wherever `native-image` lives on PATH. Lazily expanded (`=`, not `:=`) so
# targets that don't build the native image (help, clean, quick-deploy, ...)
# never need native-image on PATH at all.
GRAALVM_HOME  = $(shell dirname $$(dirname $$(readlink -f $$(command -v native-image))))
GIT           = git
LN_S          = ln -sfn
MKDIR_P       = mkdir -p
RM            = rm -f
RM_R          = rm -rf
SUDO          = sudo
NIXOS_REBUILD = nixos-rebuild

# Extra flags passed to the deployer (see `bin/invoker --help`).
DEPLOY_FLAGS  =

# ---------------------------------------------------------------------------
# Directories and files
# ---------------------------------------------------------------------------
srcdir        = .
bindir        = $(srcdir)/bin
nixosdir      = $(srcdir)/nixos
sysconfdir    = /etc/nixos

INVOKER       = $(bindir)/invoker
JAVA_SRC      = $(shell find $(srcdir)/src/main/java $(srcdir)/src/test/java -name '*.java')
MAIN_CLASS    = dev.petrolal.dotfiles.Main
WRAPPER_MODULE = $(srcdir)/config/gtk-3.0/libwrapper-menu-fix.so
WRAPPER_SRC    = $(srcdir)/config/gtk-3.0/wrapper-menu-fix.c
GTK3_MOD_DIR   = $(HOME)/.local/state/nix/profile/lib/gtk-3.0/modules

# ---------------------------------------------------------------------------
# Phony targets
# ---------------------------------------------------------------------------
.PHONY: all help check install uninstall installcheck \
        deploy quick-deploy dry-run scale reload reload-panel reload-wm reload-theme \
        modules bootstrap nix-link nix-switch system-install \
        mostlyclean clean distclean maintainer-clean

# Default goal: build everything, change nothing on the system.
all: $(INVOKER) $(WRAPPER_MODULE)

help:
	@echo 'Usage: make [TARGET] [VARIABLE=value]...'
	@echo ''
	@echo 'Build:'
	@echo '  all             Compile the native GraalVM invoker and GTK module (default)'
	@echo '  modules         Compile and install GTK fix module'
	@echo '  check           Run the test suite and a dry-run deploy'
	@echo ''
	@echo 'Deploy (user session, no root):'
	@echo '  install         Link dotfiles, install GTK module, and apply XFCE theme'
	@echo '  deploy          Same as install'
	@echo '  dry-run         Show what deploy would do without changing anything'
	@echo '  scale           Reset WM margins for current display, reload XFCE'
	@echo '  reload          Reload EVERYTHING (panel, xfwm4, xsettingsd, GTK, thunar, notifyd)'
	@echo '  reload-panel    Restart only xfce4-panel'
	@echo '  reload-wm       Restart only xfwm4 window manager'
	@echo '  reload-theme    Trigger instant GTK CSS reload across all windows'
	@echo '  quick-deploy    Deploy via the JVM directly, without a native-image build'
	@echo '  uninstall       Remove all dotfiles symlinks managed by deploy'
	@echo '  installcheck    Verify the installed invoker runs'
	@echo ''
	@echo 'NixOS (requires sudo):'
	@echo '  bootstrap       First-time setup from a fresh NixOS (no make/GraalVM needed)'
	@echo '  nix-link        Symlink configuration.nix and flake.nix into $(sysconfdir)'
	@echo '  nix-switch      Rebuild NixOS from the flake and switch to it'
	@echo '  system-install  nix-switch, then install'
	@echo ''
	@echo 'Cleaning:'
	@echo '  mostlyclean     Remove the Maven target/ build directory'
	@echo '  clean           mostlyclean + remove the invoker'
	@echo '  distclean       clean + remove Nix result links'
	@echo ''
	@echo 'Variables: MVN, GRAALVM_HOME, SUDO, NIXOS_REBUILD, DEPLOY_FLAGS (e.g. --no-reload)'

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
# Maven decides its own incrementality internally; these file prerequisites
# just tell Make whether to invoke it at all. The `native` profile (see
# pom.xml) places the built binary straight at $(INVOKER) via its own
# outputDirectory/imageName config, so there's nothing left for Make to do
# after `mvn` returns.
$(INVOKER): pom.xml $(JAVA_SRC)
	@echo '==> Building native invoker via Maven + GraalVM native-image...'
	$(MKDIR_P) $(bindir)
	JAVA_HOME=$(GRAALVM_HOME) $(MVN) -q -Pnative package

$(WRAPPER_MODULE): $(WRAPPER_SRC)
	@echo '==> Compiling GTK module $(WRAPPER_MODULE)...'
	nix-shell -p gtk3 gcc pkg-config --run 'gcc -shared -fPIC $$(pkg-config --cflags gtk+-3.0) $(WRAPPER_SRC) -o $(WRAPPER_MODULE) $$(pkg-config --libs gtk+-3.0)'

modules: $(WRAPPER_MODULE)
	@echo '==> Installing GTK module into user profile...'
	$(MKDIR_P) $(GTK3_MOD_DIR)
	install -m 755 $(WRAPPER_MODULE) $(GTK3_MOD_DIR)/libwrapper-menu-fix.so


# Run the test suite, then dry-run the (already built) invoker.
check: $(INVOKER)
	$(MVN) -q test
	$(INVOKER) --dry-run --no-reload

# ---------------------------------------------------------------------------
# Deploy
# ---------------------------------------------------------------------------
install: deploy modules


deploy: $(INVOKER)
	$(INVOKER) $(DEPLOY_FLAGS)

dry-run: $(INVOKER)
	$(INVOKER) --dry-run $(DEPLOY_FLAGS)

scale: $(INVOKER)
	$(INVOKER) --scale $(DEPLOY_FLAGS)

reload:
	@echo '==> Reloading all restartable desktop components...'
	-xfce4-panel -r
	-xfwm4 --replace &
	-xfsettingsd --replace &
	-pkill -f xfce4-notifyd 2>/dev/null || true
	-thunar -q 2>/dev/null || true
	-xfconf-query -c xsettings -p /Net/ThemeName -s "Adwaita" && xfconf-query -c xsettings -p /Net/ThemeName -s "Adwaita-dark"

reload-panel:
	@echo '==> Restarting XFCE panel...'
	xfce4-panel -r

reload-wm:
	@echo '==> Restarting XFWM4 window manager...'
	xfwm4 --replace &

reload-theme:
	@echo '==> Forcing GTK theme stylesheet reload...'
	-xfconf-query -c xsettings -p /Net/ThemeName -s "Adwaita" && xfconf-query -c xsettings -p /Net/ThemeName -s "Adwaita-dark"

quick-deploy:
	$(MVN) -q compile
	$(JAVA) -cp target/classes $(MAIN_CLASS) $(DEPLOY_FLAGS)

installcheck: $(INVOKER)
	$(INVOKER) --version

uninstall: $(INVOKER)
	$(INVOKER) uninstall $(DEPLOY_FLAGS)

# ---------------------------------------------------------------------------
# NixOS
# ---------------------------------------------------------------------------
# Flakes only see files git knows about; `add -N` registers new files
# without staging their contents.
bootstrap:
	@echo '==> Running first-time bootstrap (no make/GraalVM required)...'
	$(srcdir)/bootstrap.sh

nix-link:
	-$(GIT) add -N .
	$(SUDO) $(LN_S) $(abspath $(nixosdir))/configuration.nix $(sysconfdir)/configuration.nix
	$(SUDO) $(LN_S) $(abspath $(nixosdir))/flake.nix $(sysconfdir)/flake.nix

# `switch` (not `build` + switch-to-configuration) registers the new system
# profile generation and installs the boot entry, so it survives a reboot.
nix-switch: nix-link
	$(SUDO) $(NIXOS_REBUILD) switch --flake $(nixosdir)

system-install: nix-switch
	$(MAKE) install

# ---------------------------------------------------------------------------
# Cleaning (GNU levels: mostlyclean < clean < distclean < maintainer-clean)
# ---------------------------------------------------------------------------
mostlyclean:
	$(MVN) -q clean
	find $(srcdir) \( -name '*~' -o -name '#*#' \) -type f -exec $(RM) {} +

clean: mostlyclean
	$(RM) $(INVOKER)

distclean: clean
	$(RM) result result-*

maintainer-clean: distclean
	@echo 'This command is intended for maintainers to use;'
	@echo 'it deletes files that may need special tools to rebuild.'
