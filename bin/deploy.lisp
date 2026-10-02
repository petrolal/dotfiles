#!/usr/bin/env sbcl --script
;;; deploy.lisp --- Script runner for Common Lisp deployer
;;; License: GPL-3.0-or-later

(require :uiop)

(let* ((script-truename (uiop:truename* (or *load-truename* *default-pathname-defaults*)))
       (bin-dir (uiop:pathname-directory-pathname script-truename))
       (root-dir (uiop:pathname-parent-directory-pathname bin-dir))
       (deployer-source (merge-pathnames "src/deployer.lisp" root-dir)))
  (dolist (file '("src/packages.lisp"
                  "src/paths.lisp"
                  "src/linker.lisp"
                  "src/display.lisp"
                  "src/generators.lisp"
                  "src/xfconf.lisp"
                  "src/orchestrator.lisp"
                  "src/cli.lisp"))
    (load (merge-pathnames file root-dir)))
  (uiop:symbol-call :dotfiles.deployer :main (uiop:command-line-arguments)))
