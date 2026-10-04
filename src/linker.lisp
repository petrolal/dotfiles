;;; linker.lisp --- Symlink logic
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defparameter *mappings*
  '(("config/gtk-3.0/gtk.css"           . ".config/gtk-3.0/gtk.css")
    ("config/gtk-3.0/assets"            . ".config/gtk-3.0/assets")
    ("config/gtk-3.0/settings.ini"      . ".config/gtk-3.0/settings.ini")
    ("config/gtk-4.0/settings.ini"      . ".config/gtk-4.0/settings.ini")
    ("config/gtk-4.0/gtk.css"           . ".config/gtk-4.0/gtk.css")
    ("config/gtk-2.0/gtkrc"             . ".gtkrc-2.0")
    ("config/xfce4/terminal/terminalrc"  . ".config/xfce4/terminal/terminalrc")
    ("config/picom/picom.conf"          . ".config/picom/picom.conf")
    ("config/quickshell"                . ".config/quickshell")
    ;; Open Display standalone: embedded in the Settings Manager it renders blank.
    ("config/applications/xfce-display-settings.desktop"
     . ".local/share/applications/xfce-display-settings.desktop")
    ("themes/imp98/xfwm4"               . ".local/share/themes/imp98/xfwm4")
    ("themes/icons"                     . ".local/share/icons/imp98")
    ("themes/icons"                     . ".icons/imp98")
    ("fonts/w95fa.otf"                  . ".local/share/fonts/w95fa.otf")))

(defun link-file (source-rel target-rel root home &key dry-run verbose)
  "Symlink SOURCE-REL under ROOT to TARGET-REL under HOME.
Ensures destination parent directories exist. Returns T on success, NIL on failure."
  (let ((src  (merge-pathnames source-rel root))
        (dest (merge-pathnames target-rel home)))
    (unless (probe-file src)
      (when verbose
        (format *error-output* "[SKIP] Source missing: ~A~%" src))
      (return-from link-file nil))
    (if dry-run
        (progn
          (format t "[DRY-RUN] Would link: ~A -> ~A~%" dest src)
          t)
        (progn
          (ensure-directories-exist dest)
          (multiple-value-bind (out err code)
              (uiop:run-program (list "ln" "-sfn" (namestring src) (namestring dest))
                                :ignore-error-status t)
            (declare (ignore out err))
            (if (zerop code)
                (progn
                  (when verbose
                    (format t "[OK] Linked: ~A -> ~A~%" dest src))
                  t)
                (progn
                  (format *error-output* "[FAIL] Failed to link ~A -> ~A~%" dest src)
                  nil)))))))

(defun symlink-p (path)
  "Return T if PATH exists and is a symbolic link."
  (zerop (nth-value 2 (uiop:run-program (list "test" "-L" (namestring path))
                                       :ignore-error-status t))))

(defun unlink-file (source-rel target-rel root home &key dry-run verbose)
  "Remove managed symlink TARGET-REL in HOME.
Verifies that TARGET-REL is actually a symlink before deletion.
Returns T if unlinked or would unlink, NIL otherwise."
  (declare (ignore source-rel root))
  (let ((dest (merge-pathnames target-rel home)))
    (cond
      ((not (symlink-p dest))
       (when verbose
         (if (probe-file dest)
             (format *error-output* "[SKIP] Not a symlink: ~A (refusing to delete)~%" dest)
             (format t "[SKIP] Symlink does not exist: ~A~%" dest)))
       nil)
      (dry-run
       (format t "[DRY-RUN] Would remove symlink: ~A~%" dest)
       t)
      (t
       (multiple-value-bind (out err code)
           (uiop:run-program (list "rm" "-f" (namestring dest))
                             :ignore-error-status t)
         (declare (ignore out err))
         (if (zerop code)
             (progn
               (when verbose
                 (format t "[OK] Removed symlink: ~A~%" dest))
               t)
             (progn
               (format *error-output* "[FAIL] Failed to remove symlink ~A~%" dest)
               nil)))))))
