;;; deployer.lisp --- Common Lisp dotfiles and desktop environment deployer
;;; License: GPL-3.0-or-later

(require :uiop)

(defpackage :dotfiles.deployer
  (:use :cl)
  (:export #:*version*
           #:*program-name*
           #:*mappings*
           #:*xfce-settings*
           #:find-dotfiles-root
           #:user-home-directory
           #:link-file
           #:apply-xfce-settings
           #:reload-desktop-services
           #:deploy
           #:main))

(in-package :dotfiles.deployer)

(defparameter *version* "1.0.0")
(defparameter *program-name* "invoker")

(defparameter *mappings*
  '(("config/alacritty/alacritty.toml" . ".config/alacritty/alacritty.toml")
    ("config/gtk-3.0/gtk.css"          . ".config/gtk-3.0/gtk.css")
    ("config/quickshell"               . ".config/quickshell")
    ("themes/hell-borders/xfwm4"       . ".local/share/themes/hell-borders/xfwm4")
    ("themes/mac-os-9-classic"         . ".local/share/themes/Mac OS 9 Classic")
    ("themes/icons"                    . ".local/share/icons/RetroismIcons")))

(defparameter *xfce-settings*
  '(("xsettings"   "/Net/ThemeName"                 "string" "Mac OS 9 Classic")
    ("xsettings"   "/Net/IconThemeName"             "string" "RetroismIcons")
    ("xfwm4"       "/general/theme"                 "string" "hell-borders")
    ("xfce4-panel" "/panels/panel-1/size"           "int"    "28")
    ("xfce4-panel" "/panels/panel-1/background-style" "int"  "0")
    ("xfce4-panel" "/plugins/plugin-2/flat-buttons" "bool"   "false")
    ("xfce4-panel" "/plugins/plugin-3/flat-buttons" "bool"   "false")
    ("xfce4-panel" "/plugins/plugin-4/flat-buttons" "bool"   "false")
    ("xfce4-panel" "/plugins/plugin-5/digital-time-format" "int"    "3")
    ("xfce4-panel" "/plugins/plugin-5/custom-format"       "string" "%b %d %Y | %H:%M")
    ("xfce4-panel" "/plugins/plugin-6/digital-time-format" "int"    "3")
    ("xfce4-panel" "/plugins/plugin-6/custom-format"       "string" "%b %d %Y | %H:%M")))

(defun user-home-directory ()
  (uiop:ensure-directory-pathname
   (or (uiop:getenv "HOME")
       (user-homedir-pathname))))

(defun normalize-dir-parent (dir)
  (let ((last-comp (first (last (pathname-directory dir)))))
    (if (member last-comp '("src" "bin") :test #'string=)
        (uiop:pathname-parent-directory-pathname dir)
        dir)))

(defun find-dotfiles-root ()
  (let ((env-dir (uiop:getenv "DOTFILES_DIR")))
    (cond
      ((and env-dir (probe-file env-dir))
       (uiop:ensure-directory-pathname (uiop:truename* env-dir)))
      (*load-truename*
       (normalize-dir-parent (uiop:pathname-directory-pathname *load-truename*)))
      (t
       (let* ((argv (uiop:raw-command-line-arguments))
              (exec (and argv (first argv)))
              (probe (and exec (probe-file exec))))
         (if probe
             (normalize-dir-parent (uiop:pathname-directory-pathname (uiop:truename* probe)))
             (uiop:ensure-directory-pathname (uiop:getcwd))))))))

(defun command-exists-p (cmd)
  (zerop (nth-value 2 (uiop:run-program (list "which" cmd) :ignore-error-status t))))

(defun link-file (source-rel target-rel root home &key dry-run verbose)
  (let ((src  (merge-pathnames source-rel root))
        (dest (merge-pathnames target-rel home)))
    (unless (probe-file src)
      (when verbose
        (format *error-output* "[SKIP] Source missing: ~A~%" src))
      (return-from link-file nil))
    (if dry-run
        (format t "[DRY-RUN] Would link: ~A -> ~A~%" dest src)
        (progn
          (ensure-directories-exist dest)
          (multiple-value-bind (out err code)
              (uiop:run-program (list "ln" "-sfn" (namestring src) (namestring dest))
                                :ignore-error-status t)
            (declare (ignore out err))
            (if (zerop code)
                (when verbose
                  (format t "[OK] Linked: ~A -> ~A~%" dest src))
                (format *error-output* "[FAIL] Failed to link ~A -> ~A~%" dest src)))))))

(defun set-xfconf (channel property type value &key dry-run)
  (if dry-run
      (format t "[DRY-RUN] xfconf-query -c ~A -p ~A -t ~A -s ~A~%" channel property type value)
      (uiop:run-program
       (list "xfconf-query" "-c" channel "-p" property "-s" value "--create" "-t" type)
       :ignore-error-status t)))

(defun apply-xfce-settings (&key dry-run verbose)
  (unless (command-exists-p "xfconf-query")
    (when verbose
      (format t "[SKIP] xfconf-query not found, skipping desktop theme configuration.~%"))
    (return-from apply-xfce-settings nil))
  (when verbose
    (format t "Applying XFCE panel and retro theme settings...~%"))
  (dolist (setting *xfce-settings*)
    (destructuring-bind (channel prop type val) setting
      (set-xfconf channel prop type val :dry-run dry-run))))

(defun reload-desktop-services (&key dry-run verbose)
  (unless (uiop:getenv "DISPLAY")
    (when verbose
      (format t "[SKIP] No DISPLAY available, skipping desktop reload.~%"))
    (return-from reload-desktop-services nil))
  (when verbose
    (format t "Reloading XFCE services...~%"))
  (dolist (cmd '("xfsettingsd --replace"
                 "xfce4-panel -r"
                 "xfwm4 --replace"))
    (if dry-run
        (format t "[DRY-RUN] Would execute: ~A~%" cmd)
        (ignore-errors
          (uiop:run-program (format nil "nohup ~A >/dev/null 2>&1 &" cmd)
                            :force-shell t)))))

(defun deploy (&key dry-run (verbose t) (reload t))
  (when verbose
    (format t "=== Deploying Infernal Dotfiles ===~%"))
  (let ((root (find-dotfiles-root))
        (home (user-home-directory)))
    (when verbose
      (format t "Root:   ~A~%" root)
      (format t "Target: ~A~%" home))
    (dolist (mapping *mappings*)
      (link-file (car mapping) (cdr mapping) root home :dry-run dry-run :verbose verbose))
    (apply-xfce-settings :dry-run dry-run :verbose verbose)
    (when reload
      (reload-desktop-services :dry-run dry-run :verbose verbose)))
  (when verbose
    (format t "Deployment finished.~%")))

(defun print-help ()
  (format t "Usage: ~A [OPTION]...~%~%" *program-name*)
  (format t "Deploy dotfiles symlinks and configure desktop settings.~%~%")
  (format t "Options:~%")
  (format t "  -n, --dry-run     simulate deployment without modifying filesystem~%")
  (format t "  -q, --quiet       suppress non-error output~%")
  (format t "      --no-reload   do not reload XFCE services~%")
  (format t "  -h, --help        display this help text and exit~%")
  (format t "  -v, --version     display version information and exit~%"))

(defun print-version ()
  (format t "~A ~A~%" *program-name* *version*)
  (format t "License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.~%")
  (format t "This is free software: you are free to change and redistribute it.~%")
  (format t "There is NO WARRANTY, to the extent permitted by law.~%"))

(defun main (&optional (argv (uiop:command-line-arguments)))
  (let ((dry-run nil)
        (verbose t)
        (reload t))
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
        ((string= arg "--no-reload")
         (setf reload nil))
        (t
         (format *error-output* "~A: unrecognized option '~A'~%" *program-name* arg)
         (format *error-output* "Try '~A --help' for more information.~%" *program-name*)
         (uiop:quit 1))))
    (deploy :dry-run dry-run :verbose verbose :reload reload)
    (uiop:quit 0)))
