;;; packages.lisp --- Package definition
;;; License: GPL-3.0-or-later

(require :uiop)

(defpackage :dotfiles.deployer
  (:use :cl)
  (:export #:*version*
           #:*program-name*
           #:*root-targets*
           #:*excludes*
           #:*overrides*
           #:*xfce-settings*
           #:find-dotfiles-root
           #:user-home-directory
           #:command-exists-p
           #:symlink-p
           #:link-file
           #:unlink-file
           #:collect-all-mappings
           #:parse-display-resolution
           #:detect-display-resolutions
           #:determine-primary-resolution
           #:apply-dynamic-resolution-scaling
           #:generate-all-configs
           #:set-xfconf
           #:set-xfconf-array
           #:load-xfconf-xml
           #:extract-xfconf-settings
           #:apply-exported-xfconf-file
           #:find-exported-xfconf-files
           #:apply-exported-xfconf-files
           #:apply-xfce-settings
           #:remove-panel-dock
           #:apply-gnome-settings
           #:reload-desktop-services
           #:deploy
           #:uninstall
           #:main))
