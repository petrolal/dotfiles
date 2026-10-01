SHELL         := /usr/bin/env bash
DOTFILES_DIR  ?= $(CURDIR)
SBCL          ?= sbcl
RM            ?= rm -f
LN            ?= ln -sfn
MKDIR         ?= mkdir -p
GIT           ?= git
SUDO          ?= sudo
NIXOS_REBUILD ?= nixos-rebuild

BIN_DIR       := $(DOTFILES_DIR)/bin
SRC_DIR       := $(DOTFILES_DIR)/src
BIN           := $(BIN_DIR)/invoker
DEPLOY_LISP   := $(BIN_DIR)/deploy.lisp
BUILD_SRC     := $(SRC_DIR)/build.lisp
DEPLOYER_SRC  := $(SRC_DIR)/deployer.lisp

.PHONY: all help build deploy quick-deploy dry-run install nix-link nix-switch clean

all: build

help:
	@echo "Available targets:"
	@echo "  build         Compile native SBCL invoker executable"
	@echo "  deploy        Execute compiled invoker to link dotfiles and apply theme"
	@echo "  quick-deploy  Deploy dotfiles directly via SBCL script mode without compiling"
	@echo "  dry-run       Simulate deployment without modifying the filesystem"
	@echo "  nix-link      Symlink NixOS configuration and flake to /etc/nixos"
	@echo "  nix-switch    Rebuild NixOS system via flake and switch to new configuration"
	@echo "  install       Rebuild NixOS system and deploy dotfiles (nix-switch + deploy)"
	@echo "  clean         Remove compiled binaries and build artifacts"

build: $(BIN)

$(BIN): $(DEPLOYER_SRC) $(BUILD_SRC)
	@echo "==> Compiling native invoker via SBCL..."
	@$(MKDIR) $(BIN_DIR)
	@$(SBCL) --script $(BUILD_SRC)

deploy: $(BIN)
	@$(BIN)

quick-deploy:
	@$(SBCL) --script $(DEPLOY_LISP)

dry-run: $(BIN)
	@$(BIN) --dry-run

install: nix-switch deploy

nix-link:
	@$(GIT) add -N . 2>/dev/null || true
	$(SUDO) $(LN) $(DOTFILES_DIR)/nixos/configuration.nix /etc/nixos/configuration.nix
	$(SUDO) $(LN) $(DOTFILES_DIR)/nixos/flake.nix /etc/nixos/flake.nix

nix-switch: nix-link
	$(SUDO) $(NIXOS_REBUILD) build --flake $(DOTFILES_DIR)/nixos --impure
	$(SUDO) ./result/bin/switch-to-configuration switch

clean:
	$(RM) $(BIN)
	$(RM) -r result result-*
	@find . -type f -name "*.fasl" -delete
