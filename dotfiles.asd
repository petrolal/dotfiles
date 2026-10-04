;;;; dotfiles.asd --- ASDF system definition for dotfiles deployer
;;;; License: GPL-3.0-or-later

(defsystem "dotfiles"
  :version "1.2.0"
  :author "Petrola Lucas <petrolalucas@gmail.com>"
  :license "GPL-3.0-or-later"
  :description "Abyssal Biopunk / Infernal Retro dotfiles deployer and desktop orchestrator"
  :depends-on ("uiop")
  :components ((:module "src"
                :serial t
                :components ((:file "packages")
                             (:file "paths")
                             (:file "linker")
                             (:file "display")
                             (:file "generators")
                             (:file "xfconf")
                             (:file "orchestrator")
                             (:file "cli"))))
  :build-operation "program-op"
  :build-pathname "../bin/invoker"
  :entry-point "dotfiles.deployer:main")
