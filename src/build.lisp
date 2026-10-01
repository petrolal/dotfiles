;;; build.lisp --- Standalone SBCL binary compiler for invoker
;;; License: GPL-3.0-or-later

(require :uiop)

(let* ((script-truename (uiop:truename* (or *load-truename* *default-pathname-defaults*)))
       (src-dir (uiop:pathname-directory-pathname script-truename))
       (root-dir (uiop:pathname-parent-directory-pathname src-dir))
       (deployer-source (merge-pathnames "src/deployer.lisp" root-dir))
       (target-bin (merge-pathnames "bin/invoker" root-dir)))
  (format t "==> Loading deployer: ~A~%" deployer-source)
  (load deployer-source)
  (ensure-directories-exist target-bin)
  (format t "==> Compiling native executable: ~A~%" target-bin)
  (sb-ext:save-lisp-and-die target-bin
                            :toplevel (symbol-function (find-symbol "MAIN" :dotfiles.deployer))
                            :executable t
                            :save-runtime-options t
                            :compression t))
