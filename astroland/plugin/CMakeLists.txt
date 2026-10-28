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

# Real separate compilation: each of these is its own translation unit now
# (previously main.cpp #include'd every other .cpp as text, so only it was
# ever listed here). Header-only classes (Transform, Hittable) need no .cpp.
# gifdec.c has no vendor_impl.cpp-style guard of its own -- it's a plain
# two-file C library (gifdec.h declarations + gifdec.c implementation), so
# it just needs to be compiled once, same as any other source here.
add_library(astroland SHARED
	src/main.cpp
	src/AstroLayout.cpp
	src/engine/Body.cpp
	src/engine/PhysicsBody.cpp
	src/engine/Sprite.cpp
	src/engine/AnimatedSprite.cpp
	src/game/Ship.cpp
	src/game/Bullet.cpp
	src/game/Moon.cpp
	src/game/Window.cpp
	src/vendor/gifdec.c
	src/vendor/vendor_impl.cpp
)
# gifdec.c is C, not C++, but every other TU here is compiled as C++ (it was
# always textually spliced into a .cpp file under the old unity build) --
# compile it as C++ too rather than introducing a C/C++ linkage split.
set_source_files_properties(src/vendor/gifdec.c PROPERTIES LANGUAGE CXX)

target_include_directories(astroland PRIVATE
	src
	# Every internal #include is a bare filename resolved off this search
	# path, so each subfolder the source tree is split into (engine/game
	# primitives vs. vendored single-header libs) has to be listed here
	# too, rather than needing every #include rewritten with a
	# subdirectory prefix.
	src/engine
	src/game
	src/vendor
	${deps_INCLUDE_DIRS}
	/nix/store/qiknd62cc6pia2v17c9vnpvjz9f12hbk-hyprutils-0.14.2-dev/include
	/nix/store/57j09q44cvs95qnfcqq5ryy5gngwcciz-systemd-minimal-libs-260.4-dev/include
	/nix/store/3768qszhlspwxjfzqazpd1qa2hdvd35i-cairo-1.18.4-dev/include
	/nix/store/cgw1grps5wf3kiwpqrc5k0xlkr3bfzq9-glslang-16.4.0-dev/include
	/nix/store/r9fw1j956k7v5zjs2f8a0ni6j3hklz4q-lua-5.5.1/include
)
target_link_libraries(astroland PRIVATE rt PkgConfig::deps)

install(TARGETS astroland)
