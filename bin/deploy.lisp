#!/usr/bin/env sbcl --script
;;; deploy.lisp --- Script runner for Common Lisp deployer
;;; License: GPL-3.0-or-later

(require :uiop)

(let* ((script-truename (uiop:truename* (or *load-truename* *default-pathname-defaults*)))
       (bin-dir (uiop:pathname-directory-pathname script-truename))
       (root-dir (uiop:pathname-parent-directory-pathname bin-dir))
       (deployer-source (merge-pathnames "src/deployer.lisp" root-dir)))
  (load deployer-source)
  (uiop:symbol-call :dotfiles.deployer :main (uiop:command-line-arguments)))
