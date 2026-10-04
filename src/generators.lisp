;;; generators.lisp --- Config file generation
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defun generate-terminalrc-content ()
  "[Configuration]
ColorBackground=#242424
ColorForeground=#FFFFFF
ColorCursor=#FFFFFF
ColorCursorForeground=#242424
ColorSelection=#9E2A2B
ColorSelectionUseBackground=FALSE
ColorBold=#FFFFFF
ColorBoldUseCycle=FALSE
ColorPalette=#242424;#9E2A2B;#606C38;#BD7B2A;#6F3646;#913348;#4D7C7A;#D6CBBB;#555555;#C0392B;#829C42;#E09F3E;#8C4F62;#B84A62;#6EA3A0;#F5EBE0
FontName=JetBrainsMono Nerd Font 10
ScrollingBar=TERMINAL_SCROLLBAR_NONE
ScrollingOnOutput=TRUE
ScrollingUnlimited=TRUE
MiscAlwaysShowTabs=FALSE
MiscBell=FALSE
MiscBordersDefault=TRUE
MiscCursorBlinks=TRUE
MiscCursorShape=TERMINAL_CURSOR_SHAPE_BLOCK
MiscDefaultGeometry=90x28
MiscInheritGeometry=FALSE
MiscMenubarDefault=FALSE
MiscMouseAutohide=TRUE
MiscToolbarDefault=FALSE
MiscConfirmClose=TRUE
MiscCycleTabs=TRUE
MiscTabCloseButtons=TRUE
MiscTabCloseMiddleClick=TRUE
MiscMiddleClickOpensUri=TRUE
MiscRightClickAction=TERMINAL_RIGHT_CLICK_ACTION_CONTEXT_MENU
MiscShowUnsafePasteDialog=TRUE
TitleMode=TERMINAL_TITLE_REPLACE
")

(defun ensure-file-content (target-pathname content &key dry-run verbose)
  "Ensure TARGET-PATHNAME exists and has CONTENT. Avoids rewriting if content is unchanged."
  (if dry-run
      (format t "[DRY-RUN] Would generate ~A~%" target-pathname)
      (let ((existing-content (and (probe-file target-pathname)
                                   (ignore-errors (uiop:read-file-string target-pathname)))))
        (if (and existing-content (string= existing-content content))
            (when verbose
              (format t "[UP-TO-DATE] Generated config unchanged: ~A~%" target-pathname))
            (progn
              (ensure-directories-exist target-pathname)
              (with-open-file (out target-pathname :direction :output :if-exists :supersede :if-does-not-exist :create)
                (write-string content out))
              (when verbose
                (format t "[GEN] Generated: ~A~%" target-pathname)))))))

(defun ensure-terminalrc (root &key dry-run verbose)
  (let ((target (merge-pathnames "config/xfce4/terminal/terminalrc" root)))
    (ensure-file-content target (generate-terminalrc-content) :dry-run dry-run :verbose verbose)))

(defun generate-all-configs (&key (root (find-dotfiles-root)) dry-run verbose)
  (when verbose
    (format t "Ensuring templated configuration assets...~%"))
  (ensure-terminalrc root :dry-run dry-run :verbose verbose))
