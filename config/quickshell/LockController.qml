pragma Singleton

import QtQuick
import Quickshell
import Quickshell.Io
import Quickshell.Wayland
import "./LockContext.qml"
import "./LockSurface.qml"

Singleton {
    Process {
        id: pauseCmd
    }
    Process {
        id: screencopyCmd
        running: false
        command: ["grim", "-s", "0.5", "-t", "jpeg", "/tmp/lock_screencopy.jpeg"]
        onExited: () => {
            pauseCmd.command = ["rmpc", "pause"];
            pauseCmd.running = true;
            lock.locked = true;
        }
    }
    IpcHandler {
        target: "lockscreen"

        function lock(): void {
            screencopyCmd.running = true;
        }
    }

    LockContext {
        id: lockContext
        onUnlocked: () => {
            lock.locked = false;
            pauseCmd.command = ["rmpc", "unpause"];
            pauseCmd.running = true;
        }
    }

    WlSessionLock {
        id: lock
        locked: false

        WlSessionLockSurface {
            id: lockSurface
            color: "transparent"
            LockSurface {
                anchors.fill: parent
                context: lockContext
            }
        }
    }

    // Empty function to define first reference to singleton
    function init() {
    }
}
