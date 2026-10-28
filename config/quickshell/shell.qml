import QtQuick
import Quickshell
import Quickshell.Io
import "./NotifsOverlay.qml"
import "./InfoOverlay.qml"
import "./LockContext.qml"
import "./LockSurface.qml"
import "./LockController.qml"
import "./ScreensaverComponent.qml"

ShellRoot {
    NotifsOverlay {}

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

    IpcHandler {
        target: "screensaver"

        function on(): void {
            screensaver.visible = true;
            infoOverlay.showInfo = false;
        }
        function off(): void {
            screensaver.visible = false;
            infoOverlay.showInfo = true;
        }
    }
    PanelWindow {
        id: screensaver
        exclusionMode: ExclusionMode.Ignore
        color: "black"
        visible: false

        anchors {
            left: true
            top: true
            bottom: true
            right: true
        }

        ScreensaverComponent {
            visible: screensaver.visible
        }
    }
}
