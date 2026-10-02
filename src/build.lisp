;;; build.lisp --- Standalone SBCL binary compiler for invoker
;;; License: GPL-3.0-or-later

(require :uiop)

(let* ((script-truename (uiop:truename* (or *load-truename* *default-pathname-defaults*)))
       (src-dir (uiop:pathname-directory-pathname script-truename))
       (root-dir (uiop:pathname-parent-directory-pathname src-dir))
       (deployer-source (merge-pathnames "src/deployer.lisp" root-dir))
       (target-bin (merge-pathnames "bin/invoker" root-dir)))
  (format t "==> Loading modules...~%")
  (dolist (file '("src/packages.lisp"
                  "src/paths.lisp"
                  "src/linker.lisp"
                  "src/display.lisp"
                  "src/generators.lisp"
                  "src/xfconf.lisp"
                  "src/orchestrator.lisp"
                  "src/cli.lisp"))
    (load (merge-pathnames file root-dir)))
  (ensure-directories-exist target-bin)
  (format t "==> Compiling native executable: ~A~%" target-bin)
  (sb-ext:save-lisp-and-die target-bin
                            :toplevel (symbol-function (find-symbol "MAIN" :dotfiles.deployer))
                            :executable t
                            :save-runtime-options t
                            :compression t))
