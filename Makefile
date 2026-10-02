# Makefile --- Build and deploy the Abyssal Biopunk dotfiles
# License: GPL-3.0-or-later
#
# Follows the GNU Coding Standards, "Makefile Conventions":
#   https://www.gnu.org/prep/standards/html_node/Makefile-Conventions.html
#
# Every variable below can be overridden on the command line, e.g.
#   make deploy DEPLOY_FLAGS=--dry-run
#   make check SBCL=/opt/sbcl/bin/sbcl

SHELL = /bin/sh

# Clear built-in suffix rules; nothing here uses them.
.SUFFIXES:

# Remove a half-written target (e.g. a truncated invoker) if its recipe fails.
.DELETE_ON_ERROR:

# ---------------------------------------------------------------------------
# Programs
# ---------------------------------------------------------------------------
SBCL          = sbcl
SBCL_FLAGS    = --noinform --non-interactive
GIT           = git
LN_S          = ln -sfn
MKDIR_P       = mkdir -p
RM            = rm -f
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
DEPLOY_SCRIPT = $(bindir)/deploy.lisp
BUILD_SRC     = $(srcdir)/src/build.lisp
DEPLOYER_SRC  = $(srcdir)/src/deployer.lisp

# ---------------------------------------------------------------------------
# Phony targets
# ---------------------------------------------------------------------------
.PHONY: all help check install uninstall installcheck \
        deploy quick-deploy dry-run scale \
        nix-link nix-switch system-install \
        mostlyclean clean distclean maintainer-clean

# Default goal: build everything, change nothing on the system.
all: $(INVOKER)

help:
	@echo 'Usage: make [TARGET] [VARIABLE=value]...'
	@echo ''
	@echo 'Build:'
	@echo '  all             Compile the native SBCL invoker (default)'
	@echo '  check           Compile-check the deployer and run a dry-run deploy'
	@echo ''
	@echo 'Deploy (user session, no root):'
	@echo '  install         Link dotfiles and apply the XFCE theme (= deploy)'
	@echo '  deploy          Same as install'
	@echo '  dry-run         Show what deploy would do without changing anything'
	@echo '  scale           Reset panel height (28px) and WM margins, reload XFCE'
	@echo '  quick-deploy    Deploy via SBCL script mode, without compiling'
	@echo '  installcheck    Verify the installed invoker runs'
	@echo ''
	@echo 'NixOS (requires sudo):'
	@echo '  nix-link        Symlink configuration.nix and flake.nix into $(sysconfdir)'
	@echo '  nix-switch      Rebuild NixOS from the flake and switch to it'
	@echo '  system-install  nix-switch, then install'
	@echo ''
	@echo 'Cleaning:'
	@echo '  mostlyclean     Remove compiled .fasl files'
	@echo '  clean           mostlyclean + remove the invoker'
	@echo '  distclean       clean + remove Nix result links'
	@echo ''
	@echo 'Variables: SBCL, SUDO, NIXOS_REBUILD, DEPLOY_FLAGS (e.g. --no-reload)'

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
$(INVOKER): $(DEPLOYER_SRC) $(BUILD_SRC)
	@echo '==> Compiling native invoker via SBCL...'
	$(MKDIR_P) $(bindir)
	$(SBCL) --script $(BUILD_SRC)

# Compile the deployer with warnings promoted to errors, then dry-run it.
check: $(INVOKER)
	$(SBCL) $(SBCL_FLAGS) \
	  --eval '(require :uiop)' \
	  --eval '(handler-bind ((warning (lambda (c) (error c)))) (load "$(DEPLOYER_SRC)"))'
	$(INVOKER) --dry-run --no-reload

# ---------------------------------------------------------------------------
# Deploy
# ---------------------------------------------------------------------------
install: deploy

deploy: $(INVOKER)
	$(INVOKER) $(DEPLOY_FLAGS)

dry-run: $(INVOKER)
	$(INVOKER) --dry-run $(DEPLOY_FLAGS)

scale: $(INVOKER)
	$(INVOKER) --scale $(DEPLOY_FLAGS)

quick-deploy:
	$(SBCL) --script $(DEPLOY_SCRIPT) $(DEPLOY_FLAGS)

installcheck: $(INVOKER)
	$(INVOKER) --version

# The deployer only creates symlinks; there is no automated undo yet.
uninstall:
	@echo 'uninstall: not implemented; remove the symlinks listed by `make dry-run`.' >&2
	@exit 1

# ---------------------------------------------------------------------------
# NixOS
# ---------------------------------------------------------------------------
# Flakes only see files git knows about; `add -N` registers new files
# without staging their contents.
nix-link:
	-$(GIT) add -N .
	$(SUDO) $(LN_S) $(abspath $(nixosdir))/configuration.nix $(sysconfdir)/configuration.nix
	$(SUDO) $(LN_S) $(abspath $(nixosdir))/flake.nix $(sysconfdir)/flake.nix

# `switch` (not `build` + switch-to-configuration) registers the new system
# profile generation and installs the boot entry, so it survives a reboot.
nix-switch: nix-link
	$(SUDO) $(NIXOS_REBUILD) switch --flake $(nixosdir) --impure

system-install: nix-switch
	$(MAKE) install

# ---------------------------------------------------------------------------
# Cleaning (GNU levels: mostlyclean < clean < distclean < maintainer-clean)
# ---------------------------------------------------------------------------
mostlyclean:
	find $(srcdir) -name '*.fasl' -type f -exec $(RM) {} +

clean: mostlyclean
	$(RM) $(INVOKER)

distclean: clean
	$(RM) result result-*

maintainer-clean: distclean
	@echo 'This command is intended for maintainers to use;'
	@echo 'it deletes files that may need special tools to rebuild.'
