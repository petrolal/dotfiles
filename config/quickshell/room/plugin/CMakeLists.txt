cmake_minimum_required(VERSION 3.16)
project(RetroRoom LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 6.5 REQUIRED COMPONENTS Core Quick Qml Quick3D Quick3DPrivate Test)

qt_standard_project_setup()

qt_add_qml_module(RetroRoom
    URI "RetroRoom"
    VERSION 1.0
    SOURCES
        SceneAnchorRegistry.h SceneAnchorRegistry.cpp
        CameraRig.h CameraRig.cpp
        RoomSceneController.h RoomSceneController.cpp
)

target_link_libraries(RetroRoom PRIVATE
    Qt6::Core
    Qt6::Quick
    Qt6::Qml
    Qt6::Quick3D
    Qt6::Quick3DPrivate
)

# Unit test suite
enable_testing()
add_executable(RetroRoomTest
    tests/test_retroroom.cpp
    SceneAnchorRegistry.h SceneAnchorRegistry.cpp
    CameraRig.h CameraRig.cpp
    RoomSceneController.h RoomSceneController.cpp
)

target_link_libraries(RetroRoomTest PRIVATE
    Qt6::Core
    Qt6::Quick
    Qt6::Qml
    Qt6::Quick3D
    Qt6::Quick3DPrivate
    Qt6::Test
)

add_test(NAME RetroRoomTest COMMAND RetroRoomTest)
