;;; xfconf-xml.lisp --- Auto-apply exported xfce-perchannel-xml settings
;;; License: GPL-3.0-or-later
;;;
;;; XFCE's own live settings store already writes one XML file per channel
;;; at ~/.config/xfce4/xfconf/xfce-perchannel-xml/<channel>.xml. Dropping a
;;; copy of one of those files under config/<app>/xfconf/xfce-perchannel-xml/
;;; in the repo (see xfconf-export-dir-p in linker.lisp) is enough to manage
;;; that channel declaratively: this file parses it and replays every
;;; property via xfconf-query, with no per-property Lisp code required.

(in-package :dotfiles.deployer)

(defun expand-home-tilde (val-str)
  "Expand a leading ~/ in VAL-STR to $HOME. xfconf-query passes property
values straight through to the consuming app/library (e.g. gdk_pixbuf
loading an icon path) with no shell in between, so a literal ~/ is never
expanded on its own and silently fails to resolve."
  (if (and (>= (length val-str) 2) (string= (subseq val-str 0 2) "~/"))
      (concatenate 'string (string-right-trim "/" (namestring (user-home-directory)))
                   (subseq val-str 1))
      val-str))

(defun set-xfconf (channel property type value &key dry-run)
  (let ((val-str (expand-home-tilde (princ-to-string value))))
    (if dry-run
        (format t "[DRY-RUN] xfconf-query -c ~A -p ~A -t ~A -s ~A~%" channel property type val-str)
        (uiop:run-program
         (list "xfconf-query" "-c" channel "-p" property "-s" val-str "--create" "-t" type)
         :ignore-error-status t))))

(defun set-xfconf-array (channel property type values &key dry-run verbose)
  "Set PROPERTY on CHANNEL to the xfconf array VALUES, each typed as TYPE.
Uses xfconf-query -a with repeated -t TYPE -s VALUE flags."
  (let* ((val-args (loop for v in values
                         collect "-t" collect type
                         collect "-s" collect (expand-home-tilde (princ-to-string v))))
         (cmd (append (list "xfconf-query" "-c" channel "-p" property "--create" "-a")
                      val-args)))
    (if dry-run
        (format t "[DRY-RUN] ~{~A ~}~%" cmd)
        (progn
          (when verbose
            (format t "[OK] Setting ~A ~A to ~A~%" channel property values))
          (uiop:run-program cmd :ignore-error-status t)))))

(defun %xml-skip-ws (s i)
  (loop while (and (< i (length s)) (member (char s i) '(#\Space #\Tab #\Newline #\Return)))
        do (incf i))
  i)

(defun %xml-parse-attrs (s i)
  "Parse attribute list starting at position I, just after a tag name.
Returns (values ALIST END-POS SELF-CLOSING-P)."
  (let ((attrs '()) (self-closing nil))
    (loop
      (setf i (%xml-skip-ws s i))
      (cond
        ((and (< (1+ i) (length s)) (char= (char s i) #\/) (char= (char s (1+ i)) #\>))
         (setf self-closing t i (+ i 2))
         (return))
        ((char= (char s i) #\>)
         (incf i)
         (return))
        (t
         (let* ((eq-pos (position #\= s :start i))
                (name (string-trim " " (subseq s i eq-pos)))
                (quote-char (char s (1+ eq-pos)))
                (val-start (+ eq-pos 2))
                (val-end (position quote-char s :start val-start)))
           (push (cons name (subseq s val-start val-end)) attrs)
           (setf i (1+ val-end))))))
    (values (nreverse attrs) i self-closing)))

(defun %xml-parse-element (s i)
  "Parse one element starting at the '<' of its opening tag, at position I.
Returns (values NODE END-POS), where NODE is (NAME ATTRS-ALIST CHILDREN)."
  (let* ((name-start (1+ i))
         (name-end (loop for j from name-start below (length s)
                          while (not (member (char s j) '(#\Space #\Tab #\Newline #\Return #\> #\/)))
                          finally (return j)))
         (name (subseq s name-start name-end)))
    (multiple-value-bind (attrs after-attrs self-closing) (%xml-parse-attrs s name-end)
      (if self-closing
          (values (list name attrs nil) after-attrs)
          (let ((children '()) (pos after-attrs))
            (loop
              (setf pos (%xml-skip-ws s pos))
              (let ((lt (position #\< s :start pos)))
                (unless lt (error "Malformed xfconf export XML: unterminated element ~A" name))
                (if (char= (char s (1+ lt)) #\/)
                    (let ((close-end (position #\> s :start lt)))
                      (setf pos (1+ close-end))
                      (return))
                    (multiple-value-bind (child child-end) (%xml-parse-element s lt)
                      (push child children)
                      (setf pos child-end)))))
            (values (list name attrs (nreverse children)) pos))))))

(defun %xml-node-attr (node key) (cdr (assoc key (second node) :test #'string=)))
(defun %xml-node-name (node) (first node))
(defun %xml-node-children (node) (third node))

(defun load-xfconf-xml (path)
  "Parse an xfce-perchannel-xml file at PATH. Returns the root <channel>
node as (NAME ATTRS-ALIST CHILDREN), skipping the <?xml ...?> prolog."
  (let* ((text (uiop:read-file-string path))
         (start (position #\< text)))
    (when (and start (char= (char text (1+ start)) #\?))
      (let ((end (search "?>" text :start2 start)))
        (setf start (position #\< text :start (+ end 2)))))
    (values (%xml-parse-element text start))))

(defun %walk-xfconf-node (node path settings)
  "Accumulate SETTINGS (a list of (:scalar PATH TYPE VALUE) / (:array PATH
TYPE VALUES)) by walking NODE's own value (if any) and recursing into every
nested <property> child, extending PATH with each child's name."
  (let ((type (%xml-node-attr node "type"))
        (value (%xml-node-attr node "value")))
    (cond
      ((and type (string= type "array"))
       (let ((values (loop for c in (%xml-node-children node)
                            when (string= (%xml-node-name c) "value")
                              collect (cons (%xml-node-attr c "type") (%xml-node-attr c "value")))))
         (when values
           (push (list :array path (car (first values)) (mapcar #'cdr values)) settings))))
      ((and type (not (string= type "empty")) value)
       (push (list :scalar path type value) settings)))
    (dolist (c (%xml-node-children node))
      (when (string= (%xml-node-name c) "property")
        (setf settings (%walk-xfconf-node c (format nil "~A/~A" path (%xml-node-attr c "name")) settings))))
    settings))

(defun extract-xfconf-settings (channel-node)
  "Return (values CHANNEL-NAME SETTINGS) from a parsed <channel> NODE."
  (let ((settings '()))
    (dolist (c (%xml-node-children channel-node))
      (when (string= (%xml-node-name c) "property")
        (setf settings (%walk-xfconf-node c (format nil "/~A" (%xml-node-attr c "name")) settings))))
    (values (%xml-node-attr channel-node "name") (nreverse settings))))

(defun apply-exported-xfconf-file (path &key dry-run verbose)
  "Parse and replay every property in the xfce-perchannel-xml export at
PATH via xfconf-query."
  (multiple-value-bind (channel settings) (extract-xfconf-settings (load-xfconf-xml path))
    (dolist (s settings)
      (ecase (first s)
        (:scalar (set-xfconf channel (second s) (third s) (fourth s) :dry-run dry-run))
        (:array (set-xfconf-array channel (second s) (third s) (fourth s)
                                   :dry-run dry-run :verbose verbose))))))

(defun find-exported-xfconf-files (root)
  "List every xfce-perchannel-xml export (*.xml) under ROOT's config/ tree."
  (let ((out '()))
    (labels ((walk (dir)
               (dolist (f (uiop:directory-files dir))
                 (when (string-equal (pathname-type f) "xml")
                   (push f out)))
               (dolist (d (uiop:subdirectories dir))
                 (walk d))))
      (let ((config-dir (uiop:ensure-directory-pathname (merge-pathnames "config" root))))
        (when (probe-file config-dir)
          (walk config-dir))))
    out))

(defun apply-exported-xfconf-files (root &key dry-run verbose)
  "Find and apply every exported xfce-perchannel-xml file under ROOT."
  (dolist (f (find-exported-xfconf-files root))
    (when verbose
      (format t "Applying exported xfconf settings: ~A~%" f))
    (apply-exported-xfconf-file f :dry-run dry-run :verbose verbose)))
