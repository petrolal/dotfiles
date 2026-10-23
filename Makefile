# Desktop/NixOS orchestration, extracted out of build.gradle per
# docs/scala-migration-checklist.md step 0 / docs/scala-migration-rules.md
# rule 10: these were always shell orchestration around a built binary, not
# Scala/Gradle build concerns, so they don't belong in build.sbt either.
#
# TRANSITIONAL: until docs/scala-migration-checklist.md step 5 (cli/) and
# step 9 (close-out) land, the actual app binary is still produced by
# Gradle (`./gradlew nativeCompile` -> bin/invoker), not by `sbt
# nativeImage` (which today only builds the step-0 placeholder,
# bin/invoker-scala). INVOKER below points at the real, currently-Java
# binary; repoint it at bin/invoker-scala once the CLI/Orchestrator port is
# done and validated.

SHELL := /bin/sh
INVOKER := bin/invoker
NIXOS_DIR := nixos
THEME_NAME := $(shell sed -n 's/.*NAME *= *"\([^"]*\)".*/\1/p' src/main/java/dev/petrolal/dotfiles/core/Theme.java | head -1)
FLAGS ?=

.PHONY: all build deploy dry-run uninstall quick-deploy install-check check \
        reload reload-panel reload-wm reload-theme \
        bootstrap nix-link nix-switch nix-check nix-update nix-gc system-install upgrade \
        clean mostly-clean dist-clean maintainer-clean

all: build

## ---------------------------------------------------------------------
## Build (still Gradle-owned until the app is ported -- see header note)
## ---------------------------------------------------------------------

build:
	./gradlew nativeCompile

install-check: build
	$(INVOKER) --version

check: build
	./gradlew test
	@echo "==> Running dry-run deploy check..."
	$(INVOKER) --dry-run --no-reload

## ---------------------------------------------------------------------
## Deploy
## ---------------------------------------------------------------------

deploy: build
	$(INVOKER) $(FLAGS)

dry-run: build
	$(INVOKER) --dry-run $(FLAGS)

uninstall: build
	$(INVOKER) uninstall $(FLAGS)

quick-deploy:
	DOTFILES_DIR=$(CURDIR) ./gradlew quick-deploy -Pflags="$(FLAGS)"

## ---------------------------------------------------------------------
## Desktop reload
## ---------------------------------------------------------------------

reload:
	@echo "==> Reloading all restartable desktop components..."
	-xfce4-panel -r 2>/dev/null
	xfwm4 --replace &
	xfsettingsd --replace &
	-pkill -f xfce4-notifyd 2>/dev/null
	-thunar -q 2>/dev/null
	-xfconf-query -c xsettings -p /Net/ThemeName -s "Default" 2>/dev/null
	-xfconf-query -c xsettings -p /Net/ThemeName -s "$(THEME_NAME)" 2>/dev/null

reload-panel:
	@echo "==> Restarting XFCE panel..."
	-xfce4-panel -r

reload-wm:
	@echo "==> Restarting XFWM4 window manager..."
	xfwm4 --replace &

reload-theme:
	@echo "==> Forcing GTK theme stylesheet reload..."
	xfconf-query -c xsettings -p /Net/ThemeName -s Default
	xfconf-query -c xsettings -p /Net/ThemeName -s "$(THEME_NAME)"

## ---------------------------------------------------------------------
## NixOS
## ---------------------------------------------------------------------

bootstrap:
	./bootstrap.sh

nix-link:
	-git add -N .
	@confTarget="$(CURDIR)/$(NIXOS_DIR)/configuration.nix"; \
	flakeTarget="$(CURDIR)/$(NIXOS_DIR)/flake.nix"; \
	confLink="/etc/nixos/configuration.nix"; \
	flakeLink="/etc/nixos/flake.nix"; \
	if [ "$$(readlink -f $$confLink 2>/dev/null)" != "$$confTarget" ]; then \
		sudo ln -sfn "$$confTarget" "$$confLink"; \
	fi; \
	if [ "$$(readlink -f $$flakeLink 2>/dev/null)" != "$$flakeTarget" ]; then \
		sudo ln -sfn "$$flakeTarget" "$$flakeLink"; \
	fi

nix-switch: nix-link
	sudo nixos-rebuild switch --flake $(NIXOS_DIR)

nix-check:
	nix flake check $(NIXOS_DIR)

nix-update:
	nix flake update --flake $(NIXOS_DIR)

nix-gc:
	nix-collect-garbage -d
	sudo nix-collect-garbage -d

system-install: nix-switch deploy

upgrade: nix-update system-install

## ---------------------------------------------------------------------
## Clean
## ---------------------------------------------------------------------

clean:
	./gradlew clean
	rm -f $(INVOKER)

mostly-clean: clean
	find . -type f \( -name '*~' -o -name '#*#' \) -delete

dist-clean: clean mostly-clean
	rm -f result result-*

maintainer-clean: dist-clean
