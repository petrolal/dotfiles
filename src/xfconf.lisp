;;; xfconf.lisp --- XFCE settings
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defparameter *xfce-settings*
  '(;; GTK and Theme Configuration (Adwaita-dark / imp98)
    ("xsettings"                "/Net/ThemeName"                          "string" "Adwaita-dark")
    ("xsettings"                "/Net/IconThemeName"                      "string" "imp98")
    ("xsettings"                "/Gtk/CursorThemeSize"                    "int"    "24")
    ("xsettings"                "/Gtk/ApplicationPreferDarkTheme"         "bool"   "true")

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
          (format t "[DRY-RUN] gsettings set org.gnome.desktop.interface icon-theme 'imp98'~%")
          (format t "[DRY-RUN] gsettings set org.gnome.desktop.interface gtk-theme 'Adwaita-dark'~%")
          (format t "[DRY-RUN] gsettings set org.gnome.desktop.interface color-scheme 'prefer-dark'~%")
          (format t "[DRY-RUN] gsettings set org.gnome.desktop.wm.preferences button-layout 'close:maximize'~%"))
        (progn
          (uiop:run-program '("gsettings" "set" "org.gnome.desktop.interface" "icon-theme" "imp98")
                            :ignore-error-status t)
          (uiop:run-program '("gsettings" "set" "org.gnome.desktop.interface" "gtk-theme" "Adwaita-dark")
                            :ignore-error-status t)
          (uiop:run-program '("gsettings" "set" "org.gnome.desktop.interface" "color-scheme" "prefer-dark")
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
