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
    ("themes/hell-borders/xfwm4"        . ".local/share/themes/hell-borders/xfwm4")
    ("themes/imp98/xfwm4"               . ".local/share/themes/imp98/xfwm4")
    ("themes/icons"                     . ".local/share/icons/imp98")
    ("themes/icons"                     . ".icons/imp98")))

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
