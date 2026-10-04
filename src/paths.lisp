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
  "Check whether CMD is an executable command available in PATH or at specified path."
  (when (and (stringp cmd) (plusp (length cmd)))
    (if (find #\/ cmd)
        (let ((probe (probe-file cmd)))
          (and probe (not (uiop:directory-pathname-p probe))))
        (let ((path-env (uiop:getenv "PATH")))
          (when path-env
            (dolist (dir (uiop:split-string path-env :separator ":"))
              (when (plusp (length dir))
                (let ((candidate (merge-pathnames cmd (uiop:ensure-directory-pathname dir))))
                  (when (and (probe-file candidate)
                             (not (uiop:directory-pathname-p candidate)))
                    (return-from command-exists-p t))))))
          nil))))
