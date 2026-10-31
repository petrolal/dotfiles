import QtQuick
import Quickshell
import Quickshell.Io
import Quickshell.Wayland
import "./notif"
import "./overlay"
import "./lock"
import "./screensaver"
import "./room"

ShellRoot {
    NotifsOverlay {
        id: notifsOverlay
    }

    IpcHandler {
        target: "notifs"

        function toggleDnd(): string {
            notifsOverlay.dnd = !notifsOverlay.dnd;
            return notifsOverlay.dnd ? "ATIVADO" : "DESATIVADO";
        }
        function getDnd(): string {
            return notifsOverlay.dnd ? "ATIVADO" : "DESATIVADO";
        }
        function clear(): void {
            notifsOverlay.clearAll();
        }
    }

    InfoOverlay {
        id: infoOverlay
    }

    // exit the example if the window closes
    Connections {
        target: Quickshell

        function onLastWindowClosed() {
            Qt.quit();
        }
    }

    Component.onCompleted: () => {
        LockController.init();
    }

    // // For testing lockscreen
    // LockContext {
    //     id: lockContext
    //     onUnlocked: Qt.quit()
    // }
    // FloatingWindow {
    //     LockSurface {
    //         anchors.fill: parent
    //         context: lockContext
    //     }
    // }

    property bool screensaverVisible: false
    property var activeRoomScene: null

    IpcHandler {
        target: "room"

        function travel(anchorId: string): string {
            if (!activeRoomScene) return "ERROR: NO_SCENE";
            if (activeRoomScene.controller.transitioning) return "BUSY";
            return activeRoomScene.controller.travelTo(anchorId) ? "OK" : "ERROR";
        }

        function reset(): string {
            if (!activeRoomScene) return "ERROR: NO_SCENE";
            activeRoomScene.controller.reset();
            return "OK";
        }

        function status(): string {
            if (!activeRoomScene) return "UNKNOWN";
            return activeRoomScene.controller.currentAnchor;
        }

        function toggleVhs(): string {
            if (!activeRoomScene) return "ERROR";
            activeRoomScene.vhsShaderEnabled = !activeRoomScene.vhsShaderEnabled;
            return activeRoomScene.vhsShaderEnabled ? "ON" : "OFF";
        }
    }

    Variants {
        model: Quickshell.screens

        PanelWindow {
            id: roomPanel
            required property var modelData
            screen: modelData

            property bool isPrimary: modelData === Quickshell.screens[0]

            visible: isPrimary && !screensaverVisible
            exclusionMode: ExclusionMode.Ignore
            WlrLayershell.layer: WlrLayer.Background
            color: "#050508"
            mask: Region {}

            anchors {
                left: true
                top: true
                bottom: true
                right: true
            }

            RoomScene {
                anchors.fill: parent
                Component.onCompleted: {
                    if (roomPanel.isPrimary) {
                        activeRoomScene = this;
                    }
                }
            }
        }
    }

    IpcHandler {
        target: "screensaver"

        function on(): void {
            screensaverVisible = true;
            infoOverlay.showInfo = false;
        }
        function off(): void {
            screensaverVisible = false;
            infoOverlay.showInfo = true;
        }
    }

    Variants {
        model: Quickshell.screens

        PanelWindow {
            required property var modelData
            screen: modelData

            exclusionMode: ExclusionMode.Ignore
            color: "black"
            visible: screensaverVisible

            anchors {
                left: true
                top: true
                bottom: true
                right: true
            }

            ScreensaverComponent {
                visible: screensaverVisible
            }
        }
    }
}
