;;; linker.lisp --- Symlink logic
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defparameter *root-targets*
  '(;; Open Display standalone: embedded in the Settings Manager it renders blank,
    ;; so it lives under applications/ and needs its own target root.
    ("config/applications" . ".local/share/applications")
    ("config"              . ".config")
    ("fonts"                . ".local/share/fonts")
    ("themes/imp98"         . ".local/share/themes/imp98")
    ;; Universal asset store (icons/images shared across gtk-3.0, xfce4-panel, etc.)
    ("assets/imp98"         . ".local/share/imp98"))
  "Source prefixes (relative to the repo root) that get auto-discovered and
mirrored file-by-file under the given target base (relative to $HOME).
Longest matching prefix wins, so a more specific entry can override a
general one (e.g. config/applications vs. config).")

(defparameter *excludes*
  '("config/gtk-2.0/gtkrc"              ; handled via *overrides*, non-standard target
    "config/gtk-3.0/libwrapper-menu-fix.so"  ; build artifact, installed by `make modules`
    "config/gtk-3.0/wrapper-menu-fix.c")     ; build artifact source, not runtime config
  "Source paths (relative to the repo root) skipped entirely during
auto-discovery. A directory listed here is not descended into.")

(defun xfconf-export-dir-p (relpath)
  "Return T if RELPATH is (or is under) an xfce-perchannel-xml export
directory, e.g. config/xfce4/xfconf/xfce-perchannel-xml. Any <app>/xfconf
directory following this XFCE convention holds live settings exports:
never symlinked (they get clobbered by the running session), and instead
auto-applied via xfconf-query by apply-exported-xfconf-files."
  (let ((marker "xfconf/xfce-perchannel-xml"))
    (let ((pos (search marker relpath)))
      (and pos
           (or (zerop pos) (char= (char relpath (1- pos)) #\/))))))

(defparameter *overrides*
  '(("config/gtk-2.0/gtkrc" . ".gtkrc-2.0")
    ("themes/icons"         . ".local/share/icons/imp98")
    ("themes/icons"         . ".icons/imp98")
    ;; gtk.css references images via a relative url("assets/...") — this
    ;; keeps that resolving without editing the CSS, while assets/imp98 in
    ;; the repo stays the single canonical source (root-targets above).
    ("assets/imp98"         . ".config/gtk-3.0/assets"))
  "Explicit (SOURCE-REL . TARGET-REL) pairs for paths that don't fit the
generic root-mirrored layout: renames, or a source linked to more than
one target.")

(defun excluded-p (relpath)
  "Return T if RELPATH is covered by *excludes* (itself, or a descendant of
an excluded directory), or is an xfconf export directory/file."
  (or (xfconf-export-dir-p relpath)
      (some (lambda (prefix)
              (or (string= relpath prefix)
                  (and (> (length relpath) (length prefix))
                       (string= prefix (subseq relpath 0 (length prefix)))
                       (char= (char relpath (length prefix)) #\/))))
            *excludes*)))

(defun prefix-match-p (prefix relpath)
  "Return T if RELPATH is PREFIX itself or a path under it."
  (and (>= (length relpath) (length prefix))
       (string= prefix relpath :end2 (length prefix))
       (or (= (length relpath) (length prefix))
           (char= (char relpath (length prefix)) #\/))))

(defun root-target-for (relpath)
  "Return the (PREFIX . TARGET-BASE) entry in *root-targets* whose prefix
matches RELPATH most specifically, or NIL if none matches."
  (let ((best nil))
    (dolist (entry *root-targets*)
      (when (and (prefix-match-p (car entry) relpath)
                 (or (null best) (> (length (car entry)) (length (car best)))))
        (setf best entry)))
    best))

(defun top-level-roots ()
  "*root-targets* prefixes that are not nested inside another prefix in the
list, i.e. the minimal set of directories that need walking once."
  (let ((prefixes (mapcar #'car *root-targets*)))
    (remove-if (lambda (prefix)
                 (some (lambda (other)
                         (and (not (string= other prefix))
                              (prefix-match-p other prefix)))
                       prefixes))
               prefixes)))

(defun scan-tree (relpath root)
  "Recursively list every non-excluded file under RELPATH (a directory,
relative to ROOT), as a list of relative path strings."
  (let ((dir (uiop:ensure-directory-pathname (merge-pathnames relpath root)))
        (out '()))
    (when (probe-file dir)
      (dolist (f (uiop:directory-files dir))
        (let ((child-rel (format nil "~A/~A" relpath (file-namestring f))))
          (unless (excluded-p child-rel)
            (push child-rel out))))
      (dolist (d (uiop:subdirectories dir))
        (let* ((dname (first (last (pathname-directory d))))
               (child-rel (format nil "~A/~A" relpath dname)))
          (unless (excluded-p child-rel)
            (setf out (nconc out (scan-tree child-rel root)))))))
    out))

(defun collect-all-mappings (root)
  "Build the full (SOURCE-REL . TARGET-REL) list: *overrides* plus every file
auto-discovered under the *root-targets* prefixes, each resolved against its
most specific matching target."
  (append
   (copy-alist *overrides*)
   (loop for file-rel in (loop for top in (top-level-roots)
                                append (scan-tree top root))
         for entry = (root-target-for file-rel)
         when entry
           collect (cons file-rel
                         (concatenate 'string (cdr entry)
                                      (subseq file-rel (length (car entry))))))))

(defun symlink-p (path)
  "Return T if PATH exists and is a symbolic link."
  (zerop (nth-value 2 (uiop:run-program (list "test" "-L" (namestring path))
                                       :ignore-error-status t))))

(defun directory-exists-p (path)
  "Return T if PATH exists on disk and is a directory (symlink or not)."
  (zerop (nth-value 2 (uiop:run-program (list "test" "-d" (namestring path))
                                       :ignore-error-status t))))

(defun ensure-real-directory (dir home)
  "Ensure DIR (under HOME) exists as a real directory, replacing any
symlinked ancestor with a real one first. Needed because an earlier
whole-directory link scheme left some of these as symlinks straight into
the repo; leaving such an ancestor in place would make a new leaf symlink
resolve back onto its own source file."
  (let* ((home-str (string-right-trim "/" (namestring home)))
         (dir-str (string-right-trim "/" (namestring (uiop:ensure-directory-pathname dir))))
         (suffix (if (and (>= (length dir-str) (length home-str))
                           (string= home-str dir-str :end2 (length home-str)))
                     (subseq dir-str (length home-str))
                     dir-str))
         (current home-str))
    (dolist (part (remove "" (uiop:split-string suffix :separator "/") :test #'string=))
      (setf current (concatenate 'string current "/" part))
      (when (symlink-p current)
        (uiop:run-program (list "rm" "-f" current) :ignore-error-status t)))
    (uiop:run-program (list "mkdir" "-p" current) :ignore-error-status t)))

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
          (ensure-real-directory (uiop:pathname-directory-pathname dest) home)
          ;; If SRC is a directory and DEST already exists as a real (non-symlink)
          ;; directory — e.g. left over from before this path became a directory-level
          ;; link — `ln -sfn` can't replace it and would nest the new symlink inside it
          ;; instead. Clear it out first so the symlink lands at DEST itself.
          (let ((dest-str (string-right-trim "/" (namestring dest))))
            (when (and (directory-exists-p src)
                       (directory-exists-p dest-str)
                       (not (symlink-p dest-str)))
              (uiop:run-program (list "rm" "-rf" dest-str) :ignore-error-status t)))
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

(defun unlink-file (source-rel target-rel root home &key dry-run verbose)
  "Remove managed symlink TARGET-REL in HOME.
Verifies that TARGET-REL is actually a symlink before deletion.
Returns :SKIPPED if there was nothing to do, T if unlinked or would unlink,
or NIL if removal was attempted and failed."
  (declare (ignore source-rel root))
  (let ((dest (merge-pathnames target-rel home)))
    (cond
      ((not (symlink-p dest))
       (when verbose
         (if (probe-file dest)
             (format *error-output* "[SKIP] Not a symlink: ~A (refusing to delete)~%" dest)
             (format t "[SKIP] Symlink does not exist: ~A~%" dest)))
       :skipped)
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
