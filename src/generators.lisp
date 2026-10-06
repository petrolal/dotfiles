;;; generators.lisp --- Config file generation
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defun ensure-file-content (target-pathname content &key dry-run verbose)
  "Ensure TARGET-PATHNAME exists and has CONTENT. Avoids rewriting if content is unchanged.
Returns T on success, NIL on failure."
  (if dry-run
      (progn
        (format t "[DRY-RUN] Would generate ~A~%" target-pathname)
        t)
      (let ((existing-content (and (probe-file target-pathname)
                                   (ignore-errors (uiop:read-file-string target-pathname)))))
        (if (and existing-content (string= existing-content content))
            (progn
              (when verbose
                (format t "[UP-TO-DATE] Generated config unchanged: ~A~%" target-pathname))
              t)
            (handler-case
                (progn
                  (ensure-directories-exist target-pathname)
                  (with-open-file (out target-pathname :direction :output :if-exists :supersede :if-does-not-exist :create)
                    (write-string content out))
                  (when verbose
                    (format t "[GEN] Generated: ~A~%" target-pathname))
                  t)
              (error (c)
                (format *error-output* "[FAIL] Failed generating ~A: ~A~%" target-pathname c)
                nil))))))

(defun generate-all-configs (&key (root (find-dotfiles-root)) dry-run verbose)
  "Ensure all templated configurations are generated. Returns T on success, NIL on failure.
Currently a no-op: every config file under config/ is a plain tracked dotfile
handled by the symlink mechanism (linker.lisp) rather than generated content.
This hook exists for a future config that genuinely needs to be derived
(e.g. from theme colors) rather than duplicated verbatim."
  (declare (ignore root dry-run))
  (when verbose
    (format t "Ensuring templated configuration assets... (none defined)~%"))
  t)
