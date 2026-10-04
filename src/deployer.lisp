;;; deployer.lisp --- System loader for Common Lisp deployer
;;; License: GPL-3.0-or-later

(in-package :cl-user)

(require :uiop)

(let* ((this-dir (uiop:pathname-directory-pathname (or *load-truename* *default-pathname-defaults*)))
       (modules '("packages"
                  "paths"
                  "linker"
                  "display"
                  "generators"
                  "xfconf"
                  "orchestrator"
                  "cli")))
  (dolist (mod modules)
    (load (merge-pathnames (make-pathname :name mod :type "lisp") this-dir))))
