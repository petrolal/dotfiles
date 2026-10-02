;;; packages.lisp --- Package definition
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
