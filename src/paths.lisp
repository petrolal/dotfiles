;;; paths.lisp --- Path utilities
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

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
