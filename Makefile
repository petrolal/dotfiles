# Desktop/NixOS orchestration, extracted out of build.gradle per
# docs/scala-migration-checklist.md / docs/scala-migration-rules.md rule 10.
# The binary is built by `sbt nativeImage` -> bin/imp.

SHELL := /bin/sh
IMP := bin/imp
NIXOS_DIR := nixos
THEME_NAME := $(shell sed -n 's/.*val NAME *= *"\([^"]*\)".*/\1/p' src/main/scala/dev/petrolal/dotfiles/core/Theme.scala | head -1)
FLAGS ?=

.PHONY: all build deploy dry-run uninstall quick-deploy install-check check \
        reload reload-panel reload-wm reload-theme \
        bootstrap nix-link nix-switch nix-check nix-update nix-gc system-install upgrade \
        clean mostly-clean dist-clean maintainer-clean

all: build

## ---------------------------------------------------------------------
## Build
## ---------------------------------------------------------------------

build:
	sbt nativeImage

install-check: build
	$(IMP) --version

check: build
	sbt test
	@echo "==> Running dry-run deploy check..."
	$(IMP) --dry-run --no-reload

## ---------------------------------------------------------------------
## Deploy
## ---------------------------------------------------------------------

deploy: build
	$(IMP) $(FLAGS)

dry-run: build
	$(IMP) --dry-run $(FLAGS)

uninstall: build
	$(IMP) uninstall $(FLAGS)

quick-deploy:
	DOTFILES_DIR=$(CURDIR) sbt "run $(FLAGS)"

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
	sudo nixos-rebuild switch --flake ./$(NIXOS_DIR)

nix-check:
	nix flake check ./$(NIXOS_DIR)

nix-update:
	nix flake update --flake ./$(NIXOS_DIR)

nix-gc:
	nix-collect-garbage -d
	sudo nix-collect-garbage -d

system-install: nix-switch deploy

upgrade: nix-update system-install

## ---------------------------------------------------------------------
## Clean
## ---------------------------------------------------------------------

clean:
	sbt clean
	rm -f $(IMP) bin/invoker bin/invoker-scala

mostly-clean: clean
	find . -type f \( -name '*~' -o -name '#*#' \) -delete

dist-clean: clean mostly-clean
	rm -f result result-*

maintainer-clean: dist-clean
