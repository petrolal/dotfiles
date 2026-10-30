import QtQuick
import QtQuick.Shapes
import Quickshell
import Quickshell.Io
import Quickshell.Wayland
import Quickshell.Services.Notifications
import ".."

PanelWindow {
    id: root

    property bool dnd: false
    property list<Notification> notifs
    property Notification currentNotif: (notifs && notifs.length > 0) ? notifs[0] : null
    property string currentSummary: root.currentNotif ? (root.currentNotif.summary == "wait, it's all flat?" ? "always has been 🔫" : (root.currentNotif.summary || "")) : ""

    WlrLayershell.namespace: "shell:notifications"
    exclusionMode: ExclusionMode.Ignore
    color: "transparent"

    Process {
        id: pauseCmd
    }
    Process {
        id: clickCmd
        command: ["play", Env.assetsDir + "/click.wav"]
    }

    Process {
        id: logNotifCmd
    }

    FileView {
        id: dndFile
        path: Env.cacheDir + "/dnd"
        printErrors: false
    }

    anchors {
        left: true
        top: true
        bottom: true
        right: true
    }
    NotificationServer {
        actionsSupported: true

        onNotification: notif => {
            notif.tracked = true;

            const now = new Date();
            const pad = n => n.toString().padStart(2, '0');
            const dt = `${pad(now.getDate())}/${pad(now.getMonth() + 1)} ${pad(now.getHours())}:${pad(now.getMinutes())}`;
            const app = (notif.appName || "Sistema").replace(/[\t\n\r]/g, " ");
            const sum = (notif.summary || "(sem título)").replace(/[\t\n\r]/g, " ");
            const body = (notif.body || "").replace(/[\t\n\r]/g, " ");
            logNotifCmd.command = ["bash", "-c", `mkdir -p "$HOME/.cache/notifications" && printf "%s\\t%s\\t%s\\t%s\\n" "$1" "$2" "$3" "$4" >> "$HOME/.cache/notifications/history.tsv"`, "_", dt, app, sum, body];
            logNotifCmd.running = true;

            if (root.dnd || dndFile.text().trim() === "true") {
                try { notif.dismiss(); } catch (e) {}
                return;
            }

            root.notifs = [...root.notifs, notif];
            autoDismissTimer.restart();

            if (!root.visible) {
                pauseCmd.command = ["rmpc", "pause"];
                pauseCmd.running = true;
                root.visible = true;
                inAnim.running = true;
            }
        }
    }

    Timer {
        id: autoDismissTimer
        interval: 8000
        repeat: false
        onTriggered: root.dismiss()
    }

    Process {
        id: espeakCmd
        command: []
    }
    onCurrentSummaryChanged: () => {
        if (!currentSummary)
            return;
        espeakCmd.command = ["sh", "-c", `echo "${currentSummary}" | piper-tts -m /usr/share/piper-voices/en/en_US/ryan/high/en_US-ryan-high.onnx -f - | ffplay -autoexit -nodisp -af "acrusher=bits=8:samples=4:mode=log" -`];
        espeakCmd.running = true;
    }

    visible: false

    SequentialAnimation {
        id: inAnim
        running: false
        ParallelAnimation {
            NumberAnimation {
                target: maskRegion
                property: "anchors.bottomMargin"
                from: -1000
                to: 0
                duration: 400
                easing.type: Easing.OutExpo
            }
            NumberAnimation {
                target: container
                property: "opacity"
                from: 0
                to: 1
                duration: 1000
                easing.type: Easing.OutExpo
            }
        }
    }
    SequentialAnimation {
        id: outAnim
        running: false
        ParallelAnimation {
            NumberAnimation {
                target: maskRegion
                property: "anchors.bottomMargin"
                from: 0
                to: -1000
                duration: 400
                easing.type: Easing.OutExpo
            }
            NumberAnimation {
                target: container
                property: "opacity"
                from: 1
                to: 0
                duration: 1000
                easing.type: Easing.OutExpo
            }
        }
        PropertyAction {
            target: root
            property: "visible"
            value: false
        }
    }

    mask: Region {
        item: maskRegion
    }

    focusable: true
    Shortcut {
        sequence: "Space"
        onActivated: {
            clickCmd.running = true;
            if (!notifText.finished) {
                notifText.finish();
                espeakCmd.running = false;
            } else
                root.dismiss();
        }
    }
    Shortcut {
        sequence: "Escape"
        onActivated: {
            clickCmd.running = true;
            root.dismiss();
        }
    }

    Rectangle {
        id: container
        anchors.bottom: parent.bottom
        anchors.left: parent.left
        anchors.right: parent.right
        height: 600
        gradient: Gradient {
            GradientStop {
                position: 0.0
                color: "transparent"
            }
            GradientStop {
                position: 1.0
                color: "black"
            }
        }

        Rectangle {
            id: maskRegion
            anchors.bottom: parent.bottom
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.horizontalCenterOffset: 50
            width: 700
            height: 300
            color: "transparent"
            clip: false

            Rectangle {
                anchors.centerIn: parent
                anchors.horizontalCenterOffset: 50
                color: "#111"
                border.color: "#999"
                border.width: 8
                width: 800
                height: 200

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: {
                        clickCmd.running = true;
                        if (!notifText.finished) {
                            notifText.finish();
                            espeakCmd.running = false;
                        } else {
                            root.dismiss();
                        }
                    }
                }

                Text {
                    text: "✕"
                    color: "#888"
                    font.pointSize: 18
                    font.bold: true
                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.margins: 10
                    z: 10

                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: {
                            clickCmd.running = true;
                            root.dismiss();
                        }
                    }
                }

                SlowText {
                    id: notifText
                    fullText: root.currentNotif ? (root.currentSummary + "\n\n" + (root.currentNotif.body || "")) : ""
                    anchors.fill: parent
                    anchors.margins: 20
                }

                Shape {
                    anchors.right: parent.right
                    anchors.bottom: parent.bottom
                    anchors.margins: 20
                    ShapePath {
                        fillColor: "#999"
                        startX: 0
                        startY: 20
                        PathLine {
                            x: 20
                            y: 20
                        }
                        PathLine {
                            x: 20
                            y: 0
                        }
                    }
                }
            }

            Image {
                source: Env.assetsDir + "/radio.png"
                anchors.bottom: parent.bottom
                x: 300
                fillMode: Image.PreserveAspectFit
                transform: Rotation {
                    id: radioTransform
                    origin.x: 170
                    origin.y: 500
                }

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: {
                        clickCmd.running = true;
                        root.dismiss();
                    }
                }
            }
            SequentialAnimation {
                loops: Animation.Infinite
                running: true
                PropertyAction {
                    target: radioTransform
                    property: "angle"
                    value: 5
                }
                PauseAnimation {
                    duration: 500
                }
                PropertyAction {
                    target: radioTransform
                    property: "angle"
                    value: -5
                }
                PauseAnimation {
                    duration: 500
                }
            }
        }
    }
    component SlowText: Text {
        property string fullText
        property int currentIndex: 0
        property bool finished: currentIndex == fullText.length

        color: "#ffffff"
        wrapMode: Text.WordWrap
        font.pointSize: 24
        font.family: "BigBlueTermPlus Nerd Font"

        onFullTextChanged: () => {
            currentIndex = 0;
            timer.running = fullText ? true : false;
        }

        function finish() {
            timer.running = false;
            currentIndex = fullText.length;
            text = fullText;
        }

        Process {
            id: blipCmd
            command: []
        }

        Timer {
            id: timer
            interval: 50 // milliseconds per character
            running: false
            repeat: true
            onTriggered: {
                if (!root.currentNotif) {
                    running = false; // Stop when complete
                    return;
                }
                if (parent.currentIndex < parent.fullText.length) {
                    parent.currentIndex++;
                    parent.text = parent.fullText.substring(0, parent.currentIndex);
                } else {
                    running = false; // Stop when complete
                }
            }
        }
    }

    function clearAll() {
        if (root.currentNotif) {
            try { root.currentNotif.dismiss(); } catch (e) {}
        }
        root.notifs = [];
        root.visible = false;
        outAnim.running = false;
        autoDismissTimer.stop();
        try {
            pauseCmd.command = ["rmpc", "unpause"];
            pauseCmd.running = true;
        } catch (e) {}
    }

    function dismiss() {
        if (root.currentNotif) {
            try { root.currentNotif.dismiss(); } catch (e) {}
        }
        if (root.notifs && root.notifs.length > 0) {
            root.notifs = root.notifs.slice(1);
        } else {
            root.notifs = [];
        }
        if (!root.notifs || root.notifs.length === 0) {
            outAnim.running = true;
            autoDismissTimer.stop();
            try {
                pauseCmd.command = ["rmpc", "unpause"];
                pauseCmd.running = true;
            } catch (e) {}
        } else {
            autoDismissTimer.restart();
        }
    }

    function rand(min, max) {
        min = Math.ceil(min);
        max = Math.floor(max);
        return Math.floor(Math.random() * (max - min + 1)) + min;
    }
}
