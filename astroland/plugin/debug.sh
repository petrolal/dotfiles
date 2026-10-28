#!/bin/bash

cmake -B build && cmake --build build

if [[ $? -ne 0 ]]; then
	echo failed to compile
	exit 1
fi

/home/zac/code/Hyprland/build/Hyprland --config ../.config/hypr/hyprland.lua > log.txt 2>&1
