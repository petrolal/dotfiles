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
           #:symlink-p
           #:link-file
           #:unlink-file
           #:parse-display-resolution
           #:detect-display-resolutions
           #:determine-primary-resolution
           #:apply-dynamic-resolution-scaling
           #:generate-terminalrc-content
           #:ensure-terminalrc
           #:generate-all-configs
           #:set-xfconf
           #:set-panel-plugin-ids
           #:apply-xfce-settings
           #:remove-panel-dock
           #:apply-gnome-settings
           #:reload-desktop-services
           #:deploy
           #:uninstall
           #:main))
