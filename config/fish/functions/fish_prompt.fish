function fish_prompt --description 'Abyssal Biopunk prompt'
    set -l last_status $status

    set -l branch ''
    if functions -q fish_git_prompt
        set -l git_info (fish_git_prompt ' (%s)')
        if test -n "$git_info"
            set branch (set_color 9E2A2B)"$git_info"(set_color normal)
        end
    end

    set -l marker_color FFFFFF
    if test $last_status -ne 0
        set marker_color 9E2A2B
    end

    echo -n -s \
        (set_color 606C38) (whoami) (set_color 555555) '@' \
        (set_color 606C38) (prompt_hostname) (set_color normal) ' ' \
        (set_color BD7B2A) (prompt_pwd) (set_color normal) \
        "$branch" ' ' \
        (set_color $marker_color) '❯' (set_color normal) ' '
end
