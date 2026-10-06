;;; cli.lisp --- CLI interface
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defparameter *version* "1.2.0")
(defparameter *program-name* "invoker")

(defun print-help ()
  (format t "Usage: ~A [COMMAND|OPTION]...~%~%" *program-name*)
  (format t "Deploy Abyssal Biopunk dotfiles symlinks and configure desktop settings.~%~%")
  (format t "Commands:~%")
  (format t "  deploy            perform full deployment (generate, link, configure, reload) [default]~%")
  (format t "  uninstall         remove all deployed dotfile symlinks safely~%")
  (format t "  scale             query display resolution and reset panel height and WM margins~%")
  (format t "  generate          generate and verify templated configuration assets~%~%")
  (format t "Options:~%")
  (format t "  -u, --uninstall   remove all deployed dotfile symlinks safely~%")
  (format t "  -s, --scale       detect resolution and reset panel height and WM margins~%")
  (format t "  -g, --generate    generate/ensure templated configuration assets~%")
  (format t "  -n, --dry-run     simulate actions without modifying filesystem or xfconf~%")
  (format t "  -q, --quiet       suppress non-error output~%")
  (format t "      --links-only  only symlink dotfiles (implies --no-xfconf --no-reload)~%")
  (format t "      --no-xfconf   do not apply XFCE desktop settings via xfconf~%")
  (format t "      --no-generate do not generate templated configuration assets~%")
  (format t "      --no-reload   do not reload XFCE desktop services~%")
  (format t "  -h, --help        display this help text and exit~%")
  (format t "  -v, --version     display version information and exit~%"))

(defun print-version ()
  (format t "~A ~A (Abyssal Biopunk / Mac OS 9.2 Platinum)~%" *program-name* *version*)
  (format t "License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.~%")
  (format t "This is free software: you are free to change and redistribute it.~%")
  (format t "There is NO WARRANTY, to the extent permitted by law.~%"))

(defun main (&optional (argv (uiop:command-line-arguments)))
  (let ((dry-run nil)
        (verbose t)
        (reload t)
        (apply-settings t)
        (generate-configs t)
        (action :deploy))
    (dolist (arg argv)
      (cond
        ((member arg '("-h" "--help") :test #'string=)
         (print-help)
         (uiop:quit 0))
        ((member arg '("-v" "--version") :test #'string=)
         (print-version)
         (uiop:quit 0))
        ((member arg '("-n" "--dry-run") :test #'string=)
         (setf dry-run t))
        ((member arg '("-q" "--quiet") :test #'string=)
         (setf verbose nil))
        ((string= arg "--links-only")
         (setf apply-settings nil
               reload nil))
        ((string= arg "--no-xfconf")
         (setf apply-settings nil))
        ((string= arg "--no-generate")
         (setf generate-configs nil))
        ((string= arg "--no-reload")
         (setf reload nil))
        ((member arg '("-u" "--uninstall" "uninstall") :test #'string=)
         (setf action :uninstall))
        ((member arg '("-s" "--scale" "scale") :test #'string=)
         (setf action :scale))
        ((member arg '("-g" "--generate" "generate") :test #'string=)
         (setf action :generate))
        ((string= arg "deploy")
         (setf action :deploy))
        (t
         (format *error-output* "~A: unrecognized option '~A'~%" *program-name* arg)
         (format *error-output* "Try '~A --help' for more information.~%" *program-name*)
         (uiop:quit 1))))
    (case action
      (:uninstall
       (multiple-value-bind (ok failures removed)
           (uninstall :dry-run dry-run :verbose verbose)
         (declare (ignore failures removed))
         (unless ok
           (uiop:quit 1))))
      (:scale
       (apply-dynamic-resolution-scaling :dry-run dry-run :verbose verbose)
       (when reload
         (reload-desktop-services :dry-run dry-run :verbose verbose)))
      (:generate
       (unless (generate-all-configs :dry-run dry-run :verbose verbose)
         (uiop:quit 1)))
      (:deploy
       (multiple-value-bind (ok failures successes)
           (deploy :dry-run dry-run
                   :verbose verbose
                   :reload reload
                   :apply-settings apply-settings
                   :generate-configs generate-configs)
         (declare (ignore failures successes))
         (unless ok
           (uiop:quit 1)))))
    (uiop:quit 0)))
