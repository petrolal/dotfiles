import QtQuick
import Quickshell
import Quickshell.Io
import "./notif"
import "./overlay"
import "./lock"
import "./screensaver"

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
