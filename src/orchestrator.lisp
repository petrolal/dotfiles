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

(defun deploy (&key dry-run (verbose t) (reload t) (apply-settings t) (generate-configs t))
  "Deploy dotfiles, generate configs, apply settings, and reload services.
Returns (values SUCCESS-P FAILURES SUCCESSES)."
  (when verbose
    (format t "=== Deploying Abyssal Biopunk / Infernal Retro Dotfiles ===~%"))
  (let ((root (find-dotfiles-root))
        (home (user-home-directory))
        (failures 0)
        (successes 0))
    (when verbose
      (format t "Root:   ~A~%" root)
      (format t "Target: ~A~%" home))
    ;; Step 1: Ensure templated assets
    (when generate-configs
      (if (generate-all-configs :root root :dry-run dry-run :verbose verbose)
          (incf successes)
          (incf failures)))
    ;; Step 2: Symlink all mapped configs
    (dolist (mapping *mappings*)
      (if (link-file (car mapping) (cdr mapping) root home :dry-run dry-run :verbose verbose)
          (incf successes)
          (incf failures)))
    ;; Step 3: Apply XFCE / xfconf settings and dynamic resolution scaling
    (when apply-settings
      (apply-xfce-settings :dry-run dry-run :verbose verbose))
    ;; Step 4: Reload desktop services if appropriate
    (when (and reload apply-settings)
      (reload-desktop-services :dry-run dry-run :verbose verbose))
    (when verbose
      (if (zerop failures)
          (format t "Deployment finished successfully.~%")
          (format *error-output* "Deployment finished with ~D failure(s).~%" failures)))
    (values (zerop failures) failures successes)))

(defun uninstall (&key dry-run (verbose t))
  "Remove all symlinks installed by deploy.
Returns (values SUCCESS-P FAILURES REMOVED-COUNT)."
  (when verbose
    (format t "=== Removing Abyssal Biopunk / Infernal Retro Dotfiles Symlinks ===~%"))
  (let ((root (find-dotfiles-root))
        (home (user-home-directory))
        (failures 0)
        (removed-count 0))
    (when verbose
      (format t "Target: ~A~%" home))
    (dolist (mapping *mappings*)
      (let ((result (unlink-file (car mapping) (cdr mapping) root home
                                 :dry-run dry-run :verbose verbose)))
        (cond
          ((null result) nil)
          ((eq result t) (incf removed-count))
          (t (incf failures)))))
    (when verbose
      (if dry-run
          (format t "Dry-run complete. ~D symlink(s) would be removed.~%" removed-count)
          (format t "Uninstallation complete. ~D symlink(s) removed~@[, ~D failure(s)~].~%"
                  removed-count (and (plusp failures) failures))))
    (values (zerop failures) failures removed-count)))
