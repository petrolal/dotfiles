;;; xfconf.lisp --- XFCE settings
;;; License: GPL-3.0-or-later

(in-package :dotfiles.deployer)

(defparameter *xfce-settings*
  '(;; GTK and Theme Configuration (Adwaita-dark / imp98)
    ("xsettings"                "/Net/ThemeName"                          "string" "Adwaita-dark")
    ("xsettings"                "/Net/IconThemeName"                      "string" "imp98")
    ("xsettings"                "/Gtk/CursorThemeSize"                    "int"    "24")
    ("xsettings"                "/Gtk/FontName"                           "string" "W95FA 10")
    ("xsettings"                "/Gtk/ApplicationPreferDarkTheme"         "bool"   "true")

    ;; Notification Daemon Styling (xfce4-notifyd: 100% solid opacity)
    ("xfce4-notifyd"            "/initial-opacity"                        "double" "1.0")
    ("xfce4-notifyd"            "/notify-location"                        "int"    "2")

    ;; Window Manager Theme & Behavior (Windows 98)
    ("xfwm4"                    "/general/theme"                          "string" "imp98")
    ("xfwm4"                    "/general/title_font"                     "string" "W95FA Bold 10")
    ("xfwm4"                    "/general/button_layout"                  "string" "O|HMC")
    ("xfwm4"                    "/general/button_spacing"                 "int"    "2")
    ("xfwm4"                    "/general/button_offset"                  "int"    "3")
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

    ;; NOTE: xfce4-panel settings (panel geometry + all plugin-N properties)
    ;; are no longer listed here — they're auto-applied from the exported
    ;; config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml via
    ;; apply-exported-xfconf-files (see xfconf-xml.lisp). Edit that XML (or
    ;; just use the XFCE panel UI and re-export it) instead of this list.

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

(defun apply-dynamic-resolution-scaling (&key dry-run verbose)
  "Detect current display resolution, set the panel to 44px height
and enforce 0px window manager margins via xfconf."
  (multiple-value-bind (w h output) (determine-primary-resolution)
    (when verbose
      (format t "Display detection: ~Ax~A~@[ (~A)~] -> Panel: 44px, WM margins: 0px~%"
              w h output))
    ;; 43 renders as exactly 44px.
    (set-xfconf "xfce4-panel" "/panels/panel-1/size" "uint" "43" :dry-run dry-run)
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

(defparameter *gnome-settings*
  '(("org.gnome.desktop.interface"       "icon-theme"      "imp98")
    ("org.gnome.desktop.interface"       "gtk-theme"       "Adwaita-dark")
    ("org.gnome.desktop.interface"       "font-name"       "W95FA 10")
    ("org.gnome.desktop.interface"       "color-scheme"    "prefer-dark")
    ("org.gnome.desktop.wm.preferences"  "titlebar-font"   "W95FA Bold 10")
    ("org.gnome.desktop.wm.preferences"  "button-layout"   "close:maximize")))

(defun run-gsettings (schema key value &key dry-run)
  (if dry-run
      (format t "[DRY-RUN] gsettings set ~A ~A '~A'~%" schema key value)
      (uiop:run-program (list "gsettings" "set" schema key value)
                        :ignore-error-status t)))

(defun apply-gnome-settings (&key dry-run verbose)
  (when (command-exists-p "gsettings")
    (when verbose
      (format t "Syncing GSettings for GNOME/GTK apps...~%"))
    (dolist (setting *gnome-settings*)
      (destructuring-bind (schema key value) setting
        (run-gsettings schema key value :dry-run dry-run)))))

(defun apply-xfce-settings (&key (root (find-dotfiles-root)) dry-run verbose)
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
  ;; Replay every exported xfce-perchannel-xml file found under config/
  ;; (e.g. xfce4-panel.xml) — see xfconf-xml.lisp.
  (apply-exported-xfconf-files root :dry-run dry-run :verbose verbose)
  (apply-dynamic-resolution-scaling :dry-run dry-run :verbose verbose)
  (apply-gnome-settings :dry-run dry-run :verbose verbose))
