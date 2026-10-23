# Imp98 fish config. This file is symlinked
# from ~/.config/fish/config.fish by the dotfiles deployer (Linker.java's
# generic config -> .config rule) -- edit it directly, changes apply in
# any new shell.

if status is-interactive
    set -g fish_greeting
    if type -q fastfetch
        fastfetch
    end

    # --- Imp98 shell palette (same hexes as config/xfce4/terminal/terminalrc) ---
    set -g __imp_bg    16171d
    set -g __imp_red   9E2A2B
    set -g __imp_amber BD7B2A
    set -g __imp_fg    FFFFFF
    set -g __imp_dim   6F3646
    set -g __imp_moss  606C38
    set -g __imp_sand  D6CBBB
    set -g __imp_grey  555555

    # Syntax highlighting
    set -g fish_color_normal $__imp_fg
    set -g fish_color_command $__imp_amber
    set -g fish_color_keyword $__imp_red
    set -g fish_color_quote $__imp_moss
    set -g fish_color_redirection $__imp_fg
    set -g fish_color_end $__imp_dim
    set -g fish_color_error --bold $__imp_red
    set -g fish_color_param $__imp_sand
    set -g fish_color_comment $__imp_grey
    set -g fish_color_operator $__imp_amber
    set -g fish_color_escape E09F3E
    set -g fish_color_autosuggestion $__imp_grey
    set -g fish_color_cancel $__imp_red
    set -g fish_color_selection --background=$__imp_dim
    set -g fish_color_search_match --background=$__imp_dim
    set -g fish_pager_color_prefix $__imp_amber
    set -g fish_pager_color_completion $__imp_fg
    set -g fish_pager_color_description $__imp_grey
    set -g fish_pager_color_selected_background --background=$__imp_dim

    set -gx EDITOR nano
    set -gx VISUAL nano
    set -gx PAGER less
    
    # --- dotfiles workflow shortcuts (see README.md "Day-to-Day Usage";
    # -C lets these run from anywhere, no `cd ~/dotfiles` needed) ---
    abbr -a dotfiles    'cd ~/dotfiles'
    abbr -a dfdeploy    'make -C ~/dotfiles deploy'
    abbr -a dfdry       'make -C ~/dotfiles dry-run'
    abbr -a dfuninstall 'make -C ~/dotfiles uninstall'
    abbr -a dfswitch    'make -C ~/dotfiles nix-switch'
    abbr -a dfupdate    'make -C ~/dotfiles system-install'
    abbr -a dfupgrade   'make -C ~/dotfiles upgrade'
    abbr -a dfreload    'make -C ~/dotfiles reload'

    abbr -a ll 'ls -lah'
    abbr -a g  git
    abbr -a gs 'git status'
    abbr -a gd 'git diff'
    abbr -a ff 'fastfetch'
end
