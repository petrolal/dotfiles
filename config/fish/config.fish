# Abyssal Biopunk / Infernal Retro fish config. This file is symlinked
# from ~/.config/fish/config.fish by the dotfiles deployer (Linker.java's
# generic config -> .config rule) -- edit it directly, changes apply in
# any new shell.

if status is-interactive
    set -g fish_greeting

    # --- Abyssal Biopunk palette (same hexes as config/picom/picom.conf,
    # config/xfce4/terminal/terminalrc, config/conky/conky.conf and
    # config/glava/radial.glsl) ---
    set -g __abyssal_bg    16171d
    set -g __abyssal_red   9E2A2B
    set -g __abyssal_amber BD7B2A
    set -g __abyssal_fg    FFFFFF
    set -g __abyssal_dim   6F3646
    set -g __abyssal_moss  606C38
    set -g __abyssal_sand  D6CBBB
    set -g __abyssal_grey  555555

    # Syntax highlighting
    set -g fish_color_normal $__abyssal_fg
    set -g fish_color_command $__abyssal_amber
    set -g fish_color_keyword $__abyssal_red
    set -g fish_color_quote $__abyssal_moss
    set -g fish_color_redirection $__abyssal_fg
    set -g fish_color_end $__abyssal_dim
    set -g fish_color_error --bold $__abyssal_red
    set -g fish_color_param $__abyssal_sand
    set -g fish_color_comment $__abyssal_grey
    set -g fish_color_operator $__abyssal_amber
    set -g fish_color_escape E09F3E
    set -g fish_color_autosuggestion $__abyssal_grey
    set -g fish_color_cancel $__abyssal_red
    set -g fish_color_selection --background=$__abyssal_dim
    set -g fish_color_search_match --background=$__abyssal_dim
    set -g fish_pager_color_prefix $__abyssal_amber
    set -g fish_pager_color_completion $__abyssal_fg
    set -g fish_pager_color_description $__abyssal_grey
    set -g fish_pager_color_selected_background --background=$__abyssal_dim

    set -gx EDITOR 'emacs -nw'
    set -gx VISUAL emacs
    set -gx PAGER less

    # --- dotfiles workflow shortcuts (see README.md "Day-to-Day Usage";
    # -p lets these run from anywhere, no `cd ~/dotfiles` needed) ---
    abbr -a dotfiles    'cd ~/dotfiles'
    abbr -a dfdeploy    '~/dotfiles/gradlew -p ~/dotfiles deploy'
    abbr -a dfdry       '~/dotfiles/gradlew -p ~/dotfiles dry-run'
    abbr -a dfinstall   '~/dotfiles/gradlew -p ~/dotfiles install'
    abbr -a dfuninstall '~/dotfiles/gradlew -p ~/dotfiles uninstall'
    abbr -a dfswitch    '~/dotfiles/gradlew -p ~/dotfiles nix-switch'
    abbr -a dfupdate    '~/dotfiles/gradlew -p ~/dotfiles system-install'
    abbr -a dfupgrade   '~/dotfiles/gradlew -p ~/dotfiles upgrade'
    abbr -a dfscale     '~/dotfiles/gradlew -p ~/dotfiles scale'
    abbr -a dfreload    '~/dotfiles/gradlew -p ~/dotfiles reload'

    abbr -a ll 'ls -lah'
    abbr -a g  git
    abbr -a gs 'git status'
    abbr -a gd 'git diff'
end
