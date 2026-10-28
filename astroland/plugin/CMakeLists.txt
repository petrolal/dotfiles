cmake_minimum_required(VERSION 3.12)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(PkgConfig REQUIRED)

file(READ "${CMAKE_SOURCE_DIR}/VERSION" VERSION_RAW)
string(STRIP ${VERSION_RAW} VERSION)

project(
	astroland
	VERSION ${VERSION}
)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_FLAGS "-fpermissive")

add_compile_definitions(VERSION="${VERSION}")

pkg_check_modules(deps REQUIRED IMPORTED_TARGET
	hyprland
	libdrm
	pixman-1
	pangocairo
)

add_library(astroland SHARED "src/main.cpp")
target_include_directories(astroland PRIVATE
	src
	${deps_INCLUDE_DIRS}
	/nix/store/qiknd62cc6pia2v17c9vnpvjz9f12hbk-hyprutils-0.14.2-dev/include
	/nix/store/57j09q44cvs95qnfcqq5ryy5gngwcciz-systemd-minimal-libs-260.4-dev/include
	/nix/store/3768qszhlspwxjfzqazpd1qa2hdvd35i-cairo-1.18.4-dev/include
	/nix/store/cgw1grps5wf3kiwpqrc5k0xlkr3bfzq9-glslang-16.4.0-dev/include
	/nix/store/r9fw1j956k7v5zjs2f8a0ni6j3hklz4q-lua-5.5.1/include
)
target_link_libraries(astroland PRIVATE rt PkgConfig::deps)

install(TARGETS astroland)
