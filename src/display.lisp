;;; display.lisp --- Display detection
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defun parse-display-resolution (line)
  "Parse resolution string 'WIDTHxHEIGHT' from an xrandr output line."
  (let ((tokens (uiop:split-string line :separator " \t")))
    (dolist (tok tokens)
      (let ((x-pos (position #\x tok)))
        (when (and x-pos (> x-pos 0) (< x-pos (1- (length tok))))
          (let* ((width-str (subseq tok 0 x-pos))
                 (rest-str (subseq tok (1+ x-pos)))
                 (plus-pos (position #\+ rest-str))
                 (height-str (if plus-pos (subseq rest-str 0 plus-pos) rest-str)))
            (when (and (plusp (length width-str))
                       (plusp (length height-str))
                       (every #'digit-char-p width-str)
                       (every #'digit-char-p height-str))
              (return (values (parse-integer width-str)
                              (parse-integer height-str))))))))))

(defun detect-display-resolutions ()
  "Query connected display resolutions dynamically using xrandr.
Returns a list of plists: ((:output \"eDP-1\" :primary t :width 1920 :height 1080) ...)"
  (unless (command-exists-p "xrandr")
    (return-from detect-display-resolutions nil))
  (let* ((output (ignore-errors
                   (uiop:run-program '("xrandr" "--current")
                                     :output :string
                                     :ignore-error-status t)))
         (lines (if output (uiop:split-string output :separator '(#\Newline #\Return)) '()))
         (displays '()))
    (dolist (line lines (nreverse displays))
      (when (and (search " connected " line)
                 (not (search " disconnected " line)))
        (multiple-value-bind (w h) (parse-display-resolution line)
          (when (and w h)
            (let* ((parts (uiop:split-string line :separator " \t"))
                   (name (first parts))
                   (primary (not (null (search " primary " line)))))
              (push (list :output name :primary primary :width w :height h)
                    displays))))))))

(defun determine-primary-resolution ()
  "Determine the active display resolution.
Prefers primary connected monitor, otherwise the monitor with highest resolution,
or defaults to 1920x1080 if undetectable."
  (let ((displays (detect-display-resolutions)))
    (cond
      ((null displays)
       (values 1920 1080 nil))
      (t
       (let ((prim (find-if (lambda (d) (getf d :primary)) displays)))
         (if prim
             (values (getf prim :width) (getf prim :height) (getf prim :output))
             (let ((max-d (first (sort (copy-list displays) #'>
                                       :key (lambda (d)
                                              (* (getf d :width 0) (getf d :height 0)))))))
               (values (getf max-d :width) (getf max-d :height) (getf max-d :output)))))))))
