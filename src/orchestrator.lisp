;;; orchestrator.lisp --- Deployment orchestration
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defun reload-desktop-services (&key dry-run verbose)
  "Reload active XFCE desktop components if running in an X11 session."
  (unless (uiop:getenv "DISPLAY")
    (when verbose
      (format t "[SKIP] No DISPLAY available, skipping desktop reload.~%"))
    (return-from reload-desktop-services nil))
  (when verbose
    (format t "Reloading XFCE services...~%"))
  (dolist (cmd '("xfsettingsd --replace"
                 "xfce4-panel -r"
                 "xfwm4 --replace"
                 "pkill -f xfce4-notifyd"
                 "thunar -q"))
    (let ((binary (first (uiop:split-string cmd :separator " "))))
      (cond
        ((not (command-exists-p binary))
         (when verbose
           (format t "[SKIP] ~A not found, skipping.~%" binary)))
        (dry-run
         (format t "[DRY-RUN] Would execute: ~A~%" cmd))
        (t
         (ignore-errors
           (uiop:run-program (format nil "nohup ~A >/dev/null 2>&1 &" cmd)
                             :force-shell t)))))))

(defun deploy (&key dry-run (verbose t) (reload t))
  (when verbose
    (format t "=== Deploying Abyssal Biopunk / Infernal Retro Dotfiles ===~%"))
  (let ((root (find-dotfiles-root))
        (home (user-home-directory)))
    (when verbose
      (format t "Root:   ~A~%" root)
      (format t "Target: ~A~%" home))
    ;; Step 1: Ensure templated assets
    (generate-all-configs :root root :dry-run dry-run :verbose verbose)
    ;; Step 2: Symlink all mapped configs
    (dolist (mapping *mappings*)
      (link-file (car mapping) (cdr mapping) root home :dry-run dry-run :verbose verbose))
    ;; Step 3: Apply XFCE / xfconf settings and dynamic resolution scaling
    (apply-xfce-settings :dry-run dry-run :verbose verbose)
    ;; Step 4: Reload desktop services if appropriate
    (when reload
      (reload-desktop-services :dry-run dry-run :verbose verbose)))
  (when verbose
    (format t "Deployment finished.~%")))

(defun uninstall (&key dry-run (verbose t))
  "Remove all symlinks installed by deploy."
  (when verbose
    (format t "=== Removing Abyssal Biopunk / Infernal Retro Dotfiles Symlinks ===~%"))
  (let ((root (find-dotfiles-root))
        (home (user-home-directory))
        (removed-count 0))
    (when verbose
      (format t "Target: ~A~%" home))
    (dolist (mapping *mappings*)
      (when (unlink-file (car mapping) (cdr mapping) root home
                         :dry-run dry-run :verbose verbose)
        (incf removed-count)))
    (when verbose
      (if dry-run
          (format t "Dry-run complete. ~D symlink(s) would be removed.~%" removed-count)
          (format t "Uninstallation complete. ~D symlink(s) removed.~%" removed-count)))
    removed-count))
