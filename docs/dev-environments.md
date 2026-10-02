# Per-project dev environments (Java, Node.js, Python, Go)

Each project carries its own `shell.nix`, and direnv loads it when you `cd`
into the folder. This replaces version managers like SDKMAN! (Java), nvm/fnm
(Node.js), pyenv (Python) and gvm (Go). Nothing is installed globally, and two
projects can use different versions side by side.

What happens when you enter a project:

1. You `cd` into the folder.
2. direnv reads `.envrc`, which contains `use nix`.
3. nix-direnv builds (or reuses a cached copy of) the environment from `shell.nix`.
4. The tools are on your PATH. They disappear when you leave the folder.

| File                              | Purpose                                                                  |
| --------------------------------- | ------------------------------------------------------------------------ |
| `nixos/modules/development.nix`   | Enables direnv and flakes; defines `jvm-init`, `node-init`, `dev-init`, `devshell` |
| `nixos/templates/jvm/shell.nix`   | Java template (JDK, Gradle, Maven)                                       |
| `nixos/templates/node/shell.nix`  | Node.js template (Node, npm, optional yarn/pnpm/TypeScript)              |
| `nixos/templates/python/shell.nix`| Python template (Python, uv, auto-created venv)                          |
| `nixos/templates/go/shell.nix`    | Go template (Go, gopls)                                                  |
| `nixos/templates/full/shell.nix`  | Composer: merges every other template into one shell                     |
| `nixos/templates/*/.envrc`        | One line, `use nix`, so direnv loads the shell                           |

## One-time setup

Rebuild the system once so the `*-init` commands and `devshell` are on your PATH. Run
`git add` first: a flake only sees files git tracks, and the build fails if the
templates are untracked.

```bash
cd ~/dotfiles
git add nixos/templates nixos/modules/development.nix
make nix-switch   # = sudo nixos-rebuild switch --flake nixos --impure
```

The same rebuild is needed after you edit a template. Projects created earlier
keep their own copy of `shell.nix` and are not affected.

## Java

`jvm-init` sets up a project with a JDK (21 by default), Gradle and Maven. Both
build tools are built against the JDK you choose.

```bash
cd ~/projects/my-java-app
jvm-init        # JDK 21
jvm-init 17     # or a specific version: 8, 11, 17, 21, 25
cd .. && cd -   # re-enter so direnv loads it
```

`java`, `javac`, `gradle` and `mvn` are now available, and `JAVA_HOME` points
at the selected JDK, so IDEs and build tools pick it up.

- **Change the JDK:** edit `java ? "21"` in `shell.nix`. direnv reloads it on the next prompt.
- **Kotlin, Scala or sbt:** uncomment the matching line in `shell.nix`. Each one is built against the same JDK.
- **One-off version without editing:** `nix-shell --argstr java 17`.

## Node.js

`node-init` sets up a project with Node.js (22 by default), which includes
`npm` and `npx`.

```bash
cd ~/projects/my-node-app
node-init       # Node 22
node-init 24    # or a specific version: 22, 24, 25, 26
cd .. && cd -   # re-enter so direnv loads it
```

- **Change the version:** edit `node ? "22"` in `shell.nix`. Node 20 is no longer in nixpkgs (end-of-life).
- **yarn, pnpm, TypeScript:** uncomment the lines you need in `shell.nix`. yarn is built against the selected Node version.
- **Global packages:** `npm i -g <pkg>` installs into `.npm-global/` inside the
  project, because the Nix store is read-only. Its binaries are on your PATH,
  and so is `node_modules/.bin`, so local CLIs like `vite` or `eslint` run
  without `npx`.
- **.gitignore:** add `.npm-global/` (next to `node_modules/`).
- **One-off version without editing:** `nix-shell --argstr node 24`.

## Python

`dev-init python` sets up a project with Python (3.13 by default) and
[uv](https://docs.astral.sh/uv/). A virtualenv is created in `.venv/` and
activated on entry, so `pip install` and `uv pip install` just work.

```bash
cd ~/projects/my-python-app
dev-init python
cd .. && cd -   # re-enter so direnv loads it
```

You should see `🐍 Python 3.13.15 loaded (venv: .../.venv)`.

- **Change the version:** edit `python ? "3.13"` in `nix/python/shell.nix` (3.11 to 3.15). The venv is rebuilt automatically, so reinstall your packages (`pip install -r requirements.txt` or `uv sync`).
- **Prebuilt wheels (numpy, pandas, ...):** work out of the box. The shell sets `LD_LIBRARY_PATH` to Nix's libstdc++ and zlib, which those wheels expect and NixOS does not have in standard paths. If a package needs another C library, add it to that list in `shell.nix`.
- **uv:** uses the shell's Python. Downloading its own Python builds is disabled (`UV_PYTHON_DOWNLOADS=never`) because they do not run on NixOS.
- **pyright, ruff:** uncomment them in `shell.nix`.
- **.gitignore:** add `.venv/`.
- **One-off version without editing:** `nix-shell --argstr python 3.12`.

## Go

`dev-init go` sets up a project with Go (1.26 by default) and gopls.

```bash
cd ~/projects/my-go-app
dev-init go
cd .. && cd -
```

- **Change the version:** edit `go ? "1.26"` in `nix/go/shell.nix`. Versions older than 1.26 are no longer in nixpkgs.
- **Modules and `go install`:** go to `.go/` in the project (`GOPATH`), and its `bin/` is on your PATH.
- **delve, golangci-lint:** uncomment them in `shell.nix`.
- **.gitignore:** add `.go/`.

## Everything at once (Java, Node.js, Python, Go)

The `full` template has no tools of its own. It **imports every other
template** automatically: each folder in `nixos/templates/` that contains a
`shell.nix` is merged in (packages and shell hooks). Today that is `jvm`,
`node`, `python` and `go`.

### Adding a new language

Create one folder; nothing else changes. For example, Rust:

```nix
# nixos/templates/rust/shell.nix
{ pkgs ? import <nixpkgs> { }
, ...                       # required: lets the composer pass every argument
}:

pkgs.mkShell {
  packages = [ pkgs.rustc pkgs.cargo pkgs.rust-analyzer ];

  # Export variables here, not as mkShell attributes: attributes are lost
  # when the template is merged into `full`, the hook is kept.
  shellHook = ''
    echo "🦀 Rust $(rustc --version) loaded"
  '';
}
```

Rules for a template so it composes cleanly:

1. Accept `...` in the arguments.
2. Give its version argument a unique name (`java`, `node`, `python`, `go`, `rust`).
3. Put environment variables in `shellHook` with `export`.
4. Write installs to `${DEV_STATE_DIR:-$PWD}`, so `devshell` keeps them out of the current folder.

After `git add` and `make nix-switch`, `devshell` includes it and
`dev-init rust` accepts it.

### In a project: `dev-init`

```bash
cd ~/projects/my-app
dev-init                 # every language
dev-init python go       # only these
cd .. && cd -
```

It copies the chosen templates into the project and writes a `shell.nix` that
merges everything under `nix/`:

```
my-app/
├── .envrc
├── shell.nix            # the composer, pointed at ./nix
└── nix/
    ├── go/shell.nix
    └── python/shell.nix
```

The project is self-contained, so it keeps working if the dotfiles change.

- **Change a version:** edit the default in that language's file, e.g. `python ? "3.13"` in `nix/python/shell.nix`.
- **Add a language later:** copy its folder into `nix/`, e.g. `cp -r ~/dotfiles/nixos/templates/rust nix/`.
- **Remove one:** delete its folder from `nix/`.

`jvm-init` and `node-init` still exist and produce a single `shell.nix`; use
them for single-language Java or Node projects.

### From anywhere: `devshell`

```bash
devshell                                    # every template, default versions
devshell --argstr node 24 --argstr go 1.27  # override any version
exit                                        # leave
```

Every version argument goes to the template that declares it. On entry each
template prints its line:

```
🐍 Python 3.13.15 loaded (venv: ...)
⬢ Node.js 22.23.3 loaded (npm 10.9.9)
☕ JDK 21.0.12.1+1 loaded (JAVA_HOME=...)
🐹 Go 1.26.8 loaded
```

### Where things get installed

The Nix store is read-only, so each template sends installs to a folder:

| What                 | In a project              | `devshell`                             |
| -------------------- | ------------------------- | -------------------------------------- |
| Python `pip install` | `.venv/` (auto-activated) | `~/.local/share/devshell/.venv/`       |
| Go modules, binaries | `.go/` (`GOPATH`)         | `~/.local/share/devshell/.go/`         |
| `npm i -g`           | `.npm-global/`            | `~/.local/share/devshell/.npm-global/` |

Add `.venv/`, `.go/` and `.npm-global/` to the project's `.gitignore`. The venv
is rebuilt automatically when the Python version changes.

## Global use (outside a project)

By default `java`, `node`, `python` and `go` exist **only inside a project
folder**. Outside one, the commands are not found. There are four ways to get
them elsewhere, from lightest to heaviest.

### 1. Ad hoc, nothing installed

Good for a quick script or trying a version. Nix downloads it once and caches it.

```bash
nix-shell -p nodejs_22          # a shell with node 22; `exit` to leave
nix-shell -p jdk21 maven        # a shell with JDK 21 and Maven
nix-shell -p python313 uv       # a shell with Python 3.13 and uv
nix run nixpkgs#nodejs_24 -- -v # run one command and return
```

### 2. Everything, on demand: `devshell`

Run `devshell` in any folder to get every template (Java, Node.js, Python, Go)
until you `exit`. Nothing is written to the current folder; see
[From anywhere: `devshell`](#from-anywhere-devshell).

### 3. A system-wide default

Use this if you want `java`, `node` or `python` in every terminal. Add to
`nixos/modules/development.nix` and rebuild:

```nix
# Global JDK: puts `java` on PATH and sets JAVA_HOME for every shell.
programs.java = {
  enable = true;
  package = pkgs.jdk21;
};

environment.systemPackages = [
  jvm-init node-init dev-init devshell
  pkgs.nodejs_22
  pkgs.python3   # or with packages: (pkgs.python3.withPackages (ps: [ ps.requests ]))
  pkgs.uv
];
```

Projects still win: direnv puts the project's tools **in front of** the global
ones on PATH, so a project pinned to JDK 17, Node 24 or Python 3.12 uses that
version, and the global default applies everywhere else.

### 4. Global CLI tools (`npm -g`, `pip install` outside a project)

Outside a project, `npm i -g` and `pip install` fail because they try to write
to the read-only Nix store. Either:

- **Declarative (recommended):** install the tool from nixpkgs in
  `environment.systemPackages`, for example `pkgs.typescript`, `pkgs.pnpm`,
  `pkgs.typescript-language-server`, `pkgs.ruff`, `pkgs.black` or
  `pkgs.poetry`. It is versioned with the rest of the system.
- **npm's way:** point npm at a folder in your home and add it to PATH, for
  example in `~/.bashrc`:

  ```bash
  export NPM_CONFIG_PREFIX="$HOME/.npm-global"
  export PATH="$NPM_CONFIG_PREFIX/bin:$PATH"
  ```

  Packages with native addons may break after a Node upgrade; reinstall them if so.
- **Python's way:** with a global `python3` and `uv` (option 3), run
  `uv tool install <pkg>`. Each tool gets its own isolated venv and its command
  lands in `~/.local/bin`. Set `UV_PYTHON_DOWNLOADS=never` in `~/.bashrc` so uv
  uses the Nix Python instead of downloading one that does not run on NixOS.

## Without direnv, and troubleshooting

Without direnv, run `nix-shell` in the project folder to enter the environment
and `exit` to leave it. `nix-shell --run '<command>'` runs one command inside
it, which is handy for scripts and CI.

| Problem                                                | Fix                                                                  |
| ------------------------------------------------------ | -------------------------------------------------------------------- |
| `jvm-init` / `node-init` / `dev-init` / `devshell`: command not found | Run the rebuild in One-time setup                                    |
| Rebuild fails with a path not found under `templates/` | `git add` the template folder; flakes ignore untracked files         |
| Rebuild: `does not provide attribute ...nixosConfigurations` | Use `make nix-switch`; the flake reads `/etc/hostname` and needs `--impure` |
| `direnv: error .envrc is blocked`                      | Run `direnv allow` in the project                                    |
| `X refusing to overwrite`                              | The folder already has `shell.nix` or `.envrc`; edit it or remove it |
| `Node.js 20 support was removed`                       | Pick a version nixpkgs still ships (22 or newer)                     |
| Python: `libstdc++.so.6` / `libz.so.1`: cannot open shared object | Re-enter the shell (`direnv reload`); for another missing library, add it to `LD_LIBRARY_PATH` in `shell.nix` |
| `Go 1.25 is end-of-life` / `go_1_25 has been removed`  | Pick a version nixpkgs still ships (1.26 or newer)                   |
| Changes to `shell.nix` not picked up                   | Run `direnv reload`                                                  |
| First `cd` into a project is slow                      | Nix is downloading the toolchain; later entries use the cache        |
