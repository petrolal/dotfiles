;;; deployer.lisp --- Common Lisp dotfiles and desktop environment deployer
;;; License: GPL-3.0-or-later

(require :uiop)

(defpackage :dotfiles.deployer
  (:use :cl)
  (:export #:*version*
           #:*program-name*
           #:*mappings*
           #:*xfce-settings*
           #:find-dotfiles-root
           #:user-home-directory
           #:command-exists-p
           #:link-file
           #:parse-display-resolution
           #:detect-display-resolutions
           #:determine-primary-resolution
           #:apply-dynamic-resolution-scaling
           #:generate-terminalrc-content
           #:generate-picom-conf-content
           #:ensure-terminalrc
           #:ensure-picom-conf
           #:generate-all-configs
           #:set-xfconf
           #:set-panel-plugin-ids
           #:apply-xfce-settings
           #:remove-panel-dock
           #:apply-gnome-settings
           #:reload-desktop-services
           #:deploy
           #:main))

(in-package :dotfiles.deployer)

(defparameter *version* "1.1.0")
(defparameter *program-name* "invoker")

(defparameter *mappings*
  '(("config/gtk-3.0/gtk.css"           . ".config/gtk-3.0/gtk.css")
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
    ("themes/mac-os-9-classic"          . ".local/share/themes/Mac OS 9 Classic")
    ("themes/icons"                     . ".local/share/icons/RetroismIcons")
    ("themes/icons"                     . ".icons/RetroismIcons")))

(defparameter *xfce-settings*
  '(;; GTK and Theme Configuration (Mac OS 9 Platinum / Retroism)
    ("xsettings"                "/Net/ThemeName"                          "string" "Mac OS 9 Classic")
    ("xsettings"                "/Net/IconThemeName"                      "string" "RetroismIcons")
    ("xsettings"                "/Gtk/CursorThemeSize"                    "int"    "24")

    ;; Notification Daemon Styling (xfce4-notifyd: 100% solid opacity)
    ("xfce4-notifyd"            "/initial-opacity"                        "double" "1.0")
    ("xfce4-notifyd"            "/notify-location"                        "int"    "2")

    ;; Window Manager Theme & Behavior (Windows 98)
    ("xfwm4"                    "/general/theme"                          "string" "imp98")
    ("xfwm4"                    "/general/button_layout"                  "string" "O|HMC")
    ("xfwm4"                    "/general/button_spacing"                 "int"    "1")
    ("xfwm4"                    "/general/button_offset"                  "int"    "2")
    ("xfwm4"                    "/general/title_alignment"                "string" "left")
    ("xfwm4"                    "/general/full_width_title"               "bool"   "true")
    ("xfwm4"                    "/general/borderless_maximize"            "bool"   "true")
    ("xfwm4"                    "/general/easy_click"                     "string" "Super")
    ("xfwm4"                    "/general/snap_to_windows"                "bool"   "true")
    ("xfwm4"                    "/general/snap_to_border"                 "bool"   "true")
    ("xfwm4"                    "/general/snap_width"                     "int"    "10")

    ;; Compositor: Hard-edged drop shadow (offset 2 2, 95% opacity), 100% opacity, zero animations
    ("xfwm4"                    "/general/use_compositing"                "bool"   "true")
    ("xfwm4"                    "/general/shadow_delta_x"                 "int"    "2")
    ("xfwm4"                    "/general/shadow_delta_y"                 "int"    "2")
    ("xfwm4"                    "/general/shadow_opacity"                 "int"    "95")
    ("xfwm4"                    "/general/shadow_delta_width"             "int"    "0")
    ("xfwm4"                    "/general/shadow_delta_height"            "int"    "0")
    ("xfwm4"                    "/general/show_frame_shadow"              "bool"   "true")
    ("xfwm4"                    "/general/show_popup_shadow"              "bool"   "true")
    ("xfwm4"                    "/general/show_dock_shadow"               "bool"   "false")
    ("xfwm4"                    "/general/frame_opacity"                  "int"    "100")
    ("xfwm4"                    "/general/inactive_opacity"               "int"    "100")

    ;; Desktop Screen Margins: strictly 0px to ensure flush window tiling and full maximization
    ("xfwm4"                    "/general/margin_bottom"                  "int"    "0")
    ("xfwm4"                    "/general/margin_left"                    "int"    "0")
    ("xfwm4"                    "/general/margin_right"                   "int"    "0")
    ("xfwm4"                    "/general/margin_top"                     "int"    "0")

    ;; Panel Configuration (Single Top Bar pinned flush to top of screen, 0px top margin)
    ("xfce4-panel"              "/panels/panel-1/position"                "string" "p=6;x=0;y=0")
    ("xfce4-panel"              "/panels/panel-1/position-locked"         "bool"   "true")
    ("xfce4-panel"              "/panels/panel-1/background-style"        "int"    "0")
    ;; Windows 98 taskbar: 28px tall at 96 DPI. XFCE adds a 1px border to
    ;; this value, so 27 renders as exactly 28px. Icons stay 16px.
    ("xfce4-panel"              "/panels/panel-1/size"                    "uint"   "27")
    ("xfce4-panel"              "/panels/panel-1/icon-size"               "uint"   "16")
    ;; NOTE: plugin-ids is an xfconf array — set via set-panel-plugin-ids, not here.
    ("xfce4-panel"              "/plugins/plugin-2/flat-buttons"          "bool"   "false")
    ;; Clock plugin (plugin-8): Digital mode, single-line date+time, no 2-line wrap
    ("xfce4-panel"              "/plugins/plugin-8/mode"                  "int"    "2")
    ("xfce4-panel"              "/plugins/plugin-8/digital-layout"        "int"    "3")
    ("xfce4-panel"              "/plugins/plugin-8/digital-time-format"   "string" "%b %d %Y | %H:%M")

    ;; Hyprland-adapted Keybindings (Super+Return, Super+Q, Super+F, Workspaces 1-4)
    ("xfce4-keyboard-shortcuts" "/commands/custom/<Super>Return"          "string" "xfce4-terminal")
    ("xfce4-keyboard-shortcuts" "/commands/custom/<Primary><Alt>t"        "string" "xfce4-terminal")
    ("xfce4-keyboard-shortcuts" "/commands/custom/<Super>e"               "string" "thunar")
    ("xfce4-keyboard-shortcuts" "/commands/custom/<Shift><Super>s"        "string" "xfce4-screenshooter -r")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Super>q"                  "string" "close_window_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Super>f"                  "string" "fullscreen_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Super>1"                  "string" "workspace_1_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Super>2"                  "string" "workspace_2_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Super>3"                  "string" "workspace_3_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Super>4"                  "string" "workspace_4_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Shift><Super>1"           "string" "move_window_workspace_1_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Shift><Super>2"           "string" "move_window_workspace_2_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Shift><Super>3"           "string" "move_window_workspace_3_key")
    ("xfce4-keyboard-shortcuts" "/xfwm4/custom/<Shift><Super>4"           "string" "move_window_workspace_4_key")))

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
             (let ((max-d (first (sort (copy-list displays) #'> :key (lambda (d) (getf d :height))))))
               (values (getf max-d :width) (getf max-d :height) (getf max-d :output)))))))))

(defun set-xfconf (channel property type value &key dry-run)
  (if dry-run
      (format t "[DRY-RUN] xfconf-query -c ~A -p ~A -t ~A -s ~A~%" channel property type value)
      (uiop:run-program
       (list "xfconf-query" "-c" channel "-p" property "-s" value "--create" "-t" type)
       :ignore-error-status t)))

(defun set-panel-plugin-ids (ids &key dry-run verbose)
  "Set /panels/panel-1/plugin-ids to the given list of integer IDs as an xfconf array.
IDS is a list of integers e.g. '(1 2 3 4 5 6 8 10).
Uses xfconf-query -a with repeated -t int -s N flags."
  (let* ((id-args (loop for id in ids
                        collect "-t" collect "int"
                        collect "-s" collect (write-to-string id)))
         (cmd (append '("xfconf-query" "-c" "xfce4-panel"
                        "-p" "/panels/panel-1/plugin-ids"
                        "--create" "-a")
                      id-args)))
    (if dry-run
        (format t "[DRY-RUN] ~{~A ~}~%" cmd)
        (progn
          (when verbose
            (format t "[OK] Setting panel plugin-ids to ~A~%" ids))
          (uiop:run-program cmd :ignore-error-status t)))))

(defun apply-dynamic-resolution-scaling (&key dry-run verbose)
  "Detect current display resolution, reset the panel to the Windows 98 taskbar
height (28px) and enforce 0px window manager margins via xfconf."
  (multiple-value-bind (w h output) (determine-primary-resolution)
    (when verbose
      (format t "Display detection: ~Ax~A~@[ (~A)~] -> Panel: 28px (Windows 98), WM margins: 0px~%"
              w h output))
    ;; Overwrite any stale height (26/28/34/44) left by older deploys; 27 renders as 28px.
    (set-xfconf "xfce4-panel" "/panels/panel-1/size" "uint" "27" :dry-run dry-run)
    ;; Strictly 0px margins on all sides — prevents gaps/borders around tiled/maximized windows.
    (set-xfconf "xfwm4" "/general/margin_bottom" "int" "0" :dry-run dry-run)
    (set-xfconf "xfwm4" "/general/margin_left"   "int" "0" :dry-run dry-run)
    (set-xfconf "xfwm4" "/general/margin_right"  "int" "0" :dry-run dry-run)
    (set-xfconf "xfwm4" "/general/margin_top"    "int" "0" :dry-run dry-run)
    (values h output)))

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
FontName=Monospace 10
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

(defun generate-picom-conf-content ()
  "#################################
#   Abyssal Biopunk / Infernal Retro
#   Picom Compositor Configuration
#################################

# Shadows: hard-edged drop shadow (offset 2 2, radius 1, color #000000, opacity 0.95)
shadow = true;
shadow-radius = 1;
shadow-opacity = 0.95;
shadow-offset-x = 2;
shadow-offset-y = 2;
shadow-color = \"#000000\";

shadow-exclude = [
  \"name = 'Notification'\",
  \"class_g = 'Conky'\",
  \"class_g ?= 'Notify-osd'\",
  \"class_g = 'Cairo-clock'\",
  \"_GTK_FRAME_EXTENTS@\"
];

# Fading & Animations (Zero animations, no fading)
fading = false;
animations = false;

# Transparency / Opacity (100% opacity, no blur)
frame-opacity = 1.0;
inactive-opacity = 1.0;
active-opacity = 1.0;
inactive-opacity-override = false;

# Geometry: Strict 0px corner rounding
corner-radius = 0;
round-borders = 0;

# Blur: Zero blur
blur-method = \"none\";
blur-strength = 0;

# General Settings
backend = \"xrender\";
vsync = true;
mark-wmwin-focused = true;
mark-ovldir-focused = true;
detect-rounded-corners = false;
detect-client-opacity = true;
detect-transient = true;
use-damage = true;
log-level = \"warn\";
")

(defun ensure-file-content (target-pathname content &key dry-run verbose)
  (if dry-run
      (format t "[DRY-RUN] Would generate ~A~%" target-pathname)
      (progn
        (ensure-directories-exist target-pathname)
        (with-open-file (out target-pathname :direction :output :if-exists :supersede :if-does-not-exist :create)
          (write-string content out))
        (when verbose
          (format t "[GEN] Generated: ~A~%" target-pathname)))))

(defun ensure-terminalrc (root &key dry-run verbose)
  (let ((target (merge-pathnames "config/xfce4/terminal/terminalrc" root)))
    (ensure-file-content target (generate-terminalrc-content) :dry-run dry-run :verbose verbose)))

(defun ensure-picom-conf (root &key dry-run verbose)
  (let ((target (merge-pathnames "config/picom/picom.conf" root)))
    (ensure-file-content target (generate-picom-conf-content) :dry-run dry-run :verbose verbose)))

(defun generate-all-configs (&key (root (find-dotfiles-root)) dry-run verbose)
  (when verbose
    (format t "Ensuring templated configuration assets...~%"))
  (ensure-terminalrc root :dry-run dry-run :verbose verbose)
  (ensure-picom-conf root :dry-run dry-run :verbose verbose))

(defun remove-panel-dock (&key dry-run verbose)
  (when verbose
    (format t "Ensuring bottom dock panel is removed...~%"))
  (if dry-run
      (format t "[DRY-RUN] xfconf-query -c xfce4-panel -p /panels -a -t int -s 1~%")
      (progn
        (uiop:run-program '("xfconf-query" "-c" "xfce4-panel" "-p" "/panels" "-a" "-t" "int" "-s" "1")
                          :ignore-error-status t)
        (uiop:run-program '("xfconf-query" "-c" "xfce4-panel" "-p" "/panels/panel-2" "-r" "-R")
                          :ignore-error-status t))))

(defun apply-gnome-settings (&key dry-run verbose)
  (when (command-exists-p "gsettings")
    (when verbose
      (format t "Syncing GSettings for GNOME/GTK apps...~%"))
    (if dry-run
        (progn
          (format t "[DRY-RUN] gsettings set org.gnome.desktop.interface icon-theme 'RetroismIcons'~%")
          (format t "[DRY-RUN] gsettings set org.gnome.desktop.interface gtk-theme 'Mac OS 9 Classic'~%")
          (format t "[DRY-RUN] gsettings set org.gnome.desktop.wm.preferences button-layout 'close:maximize'~%"))
        (progn
          (uiop:run-program '("gsettings" "set" "org.gnome.desktop.interface" "icon-theme" "RetroismIcons")
                            :ignore-error-status t)
          (uiop:run-program '("gsettings" "set" "org.gnome.desktop.interface" "gtk-theme" "Mac OS 9 Classic")
                            :ignore-error-status t)
          (uiop:run-program '("gsettings" "set" "org.gnome.desktop.wm.preferences" "button-layout" "close:maximize")
                            :ignore-error-status t)))))

(defun apply-xfce-settings (&key dry-run verbose)
  (unless (command-exists-p "xfconf-query")
    (when verbose
      (format t "[SKIP] xfconf-query not found, skipping desktop theme configuration.~%"))
    (return-from apply-xfce-settings nil))
  (when verbose
    (format t "Applying XFCE panel and retro theme settings...~%"))
  (remove-panel-dock :dry-run dry-run :verbose verbose)
  (dolist (setting *xfce-settings*)
    (destructuring-bind (channel prop type val) setting
      (set-xfconf channel prop type val :dry-run dry-run)))
  ;; Set panel plugin-ids as an xfconf array: plugins 1-6, 8, 10.
  ;; Transparent separators 7 and 9 are excluded so systray(6)/clock(8)/actions(10)
  ;; sit flush together as the right-side darker container group.
  (set-panel-plugin-ids '(1 2 3 4 5 6 8 10) :dry-run dry-run :verbose verbose)
  (apply-dynamic-resolution-scaling :dry-run dry-run :verbose verbose)
  (apply-gnome-settings :dry-run dry-run :verbose verbose))

(defun reload-desktop-services (&key dry-run verbose)
  (unless (uiop:getenv "DISPLAY")
    (when verbose
      (format t "[SKIP] No DISPLAY available, skipping desktop reload.~%"))
    (return-from reload-desktop-services nil))
  (when verbose
    (format t "Reloading XFCE services...~%"))
  (dolist (cmd '("xfsettingsd --replace"
                 "xfce4-panel -r"
                 "xfwm4 --replace"
                 "pkill -f xfce4-notifyd"))
    (if dry-run
        (format t "[DRY-RUN] Would execute: ~A~%" cmd)
        (ignore-errors
          (uiop:run-program (format nil "nohup ~A >/dev/null 2>&1 &" cmd)
                            :force-shell t)))))

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

(defun print-help ()
  (format t "Usage: ~A [COMMAND|OPTION]...~%~%" *program-name*)
  (format t "Deploy Abyssal Biopunk dotfiles symlinks and configure desktop settings.~%~%")
  (format t "Commands:~%")
  (format t "  deploy            perform full deployment (generate, link, configure, reload) [default]~%")
  (format t "  scale             query display resolution and reset panel height and WM margins~%")
  (format t "  generate          generate and verify templated configuration assets~%~%")
  (format t "Options:~%")
  (format t "  -s, --scale       detect resolution and reset panel height and WM margins~%")
  (format t "  -g, --generate    generate/ensure templated configuration assets~%")
  (format t "  -n, --dry-run     simulate actions without modifying filesystem or xfconf~%")
  (format t "  -q, --quiet       suppress non-error output~%")
  (format t "      --no-reload   do not reload XFCE services~%")
  (format t "  -h, --help        display this help text and exit~%")
  (format t "  -v, --version     display version information and exit~%"))

(defun print-version ()
  (format t "~A ~A (Abyssal Biopunk / Mac OS 9.2 Platinum)~%" *program-name* *version*)
  (format t "License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>.~%")
  (format t "This is free software: you are free to change and redistribute it.~%")
  (format t "There is NO WARRANTY, to the extent permitted by law.~%"))

(defun main (&optional (argv (uiop:command-line-arguments)))
  (let ((dry-run nil)
        (verbose t)
        (reload t)
        (action :deploy))
    (dolist (arg argv)
      (cond
        ((member arg '("-h" "--help") :test #'string=)
         (print-help)
         (uiop:quit 0))
        ((member arg '("-v" "--version") :test #'string=)
         (print-version)
         (uiop:quit 0))
        ((member arg '("-n" "--dry-run") :test #'string=)
         (setf dry-run t))
        ((member arg '("-q" "--quiet") :test #'string=)
         (setf verbose nil))
        ((string= arg "--no-reload")
         (setf reload nil))
        ((member arg '("-s" "--scale" "scale") :test #'string=)
         (setf action :scale))
        ((member arg '("-g" "--generate" "generate") :test #'string=)
         (setf action :generate))
        ((string= arg "deploy")
         (setf action :deploy))
        (t
         (format *error-output* "~A: unrecognized option '~A'~%" *program-name* arg)
         (format *error-output* "Try '~A --help' for more information.~%" *program-name*)
         (uiop:quit 1))))
    (case action
      (:scale
       (apply-dynamic-resolution-scaling :dry-run dry-run :verbose verbose)
       (when reload
         (reload-desktop-services :dry-run dry-run :verbose verbose)))
      (:generate
       (generate-all-configs :dry-run dry-run :verbose verbose))
      (:deploy
       (deploy :dry-run dry-run :verbose verbose :reload reload)))
    (uiop:quit 0)))
