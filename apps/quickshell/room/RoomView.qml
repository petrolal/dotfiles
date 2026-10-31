import QtQuick
import QtQuick3D
import Quickshell
import Quickshell.Wayland
import RetroRoom 1.0

PanelWindow {
    id: root

    // Cover primary monitor at background layer
    WlrLayershell.layer: WlrLayer.Background
    exclusionMode: ExclusionMode.Ignore
    mask: Region {}

    anchors {
        top: true
        bottom: true
        left: true
        right: true
    }
    color: "#050505"

    // Controller exposed by plugins/retroroom
    property alias controller: roomScene.controller
    property alias anchorRegistry: roomScene.registry
    property alias cameraRig: roomScene.cameraRig

    // IPC Handler for Quickshell calls
    IpcHandler {
        target: "room"

        function travel(anchorId: string): string {
            if (roomScene.controller.transitioning) return "BUSY";
            return roomScene.controller.travelTo(anchorId) ? "OK" : "ERROR";
        }

        function toggleVhs(enabled: bool): string {
            roomScene.vhsShaderEnabled = enabled;
            return roomScene.vhsShaderEnabled ? "ON" : "OFF";
        }

        function reset(): string {
            roomScene.controller.reset();
            return "OK";
        }

        function status(): string {
            return roomScene.controller.currentAnchor;
        }
    }

    RoomScene {
        id: roomScene
        anchors.fill: parent
    }
}
