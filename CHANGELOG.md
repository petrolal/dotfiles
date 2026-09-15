# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Contributor Covenant v2.0 Code of Conduct (`CODE_OF_CONDUCT.md`) and Contributing documentation in `README.md`
- Transient OSD notification routing via `notification-visibility` in SwayNC configuration
- Agent instructions (AGENTS.md) for consistent AI-assisted development
- CI/CD pipeline with separate build, test, and publish stages
- Test skipping for Sway/desktop-dependent tests in CI environments

### Changed
- Refactored deploy workflow into three independent stages (build-and-test, publish-maven-central, create-release)
- Removed AUR and GitHub Pages publishing from automated pipeline (can be re-added when configured)

### Fixed
- Volume and brightness OSD notifications displaying in SwayNC notification center history (`polyomino-osd` transient hints)
- GitHub Actions workflow to use maintained `sbt/setup-sbt@v1` action
- GPG signing configuration for Maven Central publishing
- Test infrastructure to handle CI environments without Sway/desktop tools

## [0.1.0] - 2026-08-13

### Added
- **Multi-call native binary** compiled with GraalVM — single `polyomino` executable with subcommand routing
- **Dynamic window autotiling** with Fibonacci spiral layout for Sway/Wayland
- **Desktop theme switching** across application surfaces (AWS, Azure, GCP, OCI cloud themes)
- **System provisioning** via 3-stage installation (bootstrap → binary install → interactive setup)
- **Configuration management** with symlink-based dotfile deployment
- **Screen locking** integration with swaylock
- **Idle daemon management** for automated screen lock
- **Screenshot tools** with full/region/window capture modes
- **System health validation** via comprehensive healthcheck command
- **Configuration snapshots** for backup/restore workflows
- **SDKMan maintenance** system for build toolchain management
- **Maven Central publishing** via Sonatype Central Portal
- **GitHub Releases** with native binary distribution
- **Interactive installer** for safe dotfile symlink deployment with backups
- **Zero-reflection design** — 100% GraalVM native-image compatible using uPickle macros and mainargs
- **Comprehensive documentation** including architecture, installation flow, and publishing guides
- **munit-based test suite** with CI environment detection

### Changed
- **Scala 3.5.2** implementation replacing previous Rust codebase
- **sbt 1.10.2** build system with native-image plugin for GraalVM compilation
- **os-lib** for all file I/O and process execution (no JVM buffering)

### Notes
- **Startup latency**: 15–50 ms (native image, no JVM startup cost)
- **Binary size**: ~40–60 MB
- **Memory footprint**: <60 MB peak RSS
- **GraalVM flags**: `--no-fallback`, `-H:+ReportExceptionStackTraces`, `--enable-preview`
