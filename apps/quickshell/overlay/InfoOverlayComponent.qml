import QtQuick
import QtQuick.Layouts
import QtQuick.Effects
import Quickshell.Io
import ".."

Rectangle {
    id: root

    property alias showInfo: info.visible

    anchors.fill: parent
    color: "transparent"

    Rectangle {
        id: info

        anchors.fill: parent
        anchors.margins: 70
        color: "transparent"
        visible: root.showInfo

        Process {
            id: batProc
            command: ["sh", "-c", "cat /sys/class/power_supply/BAT*/capacity 2>/dev/null | head -n 1"]
            running: true
            stdout: SplitParser {
                onRead: data => {
                    const percent = parseInt(data);
                    if (percent < 40)
                        bat.source = Env.assetsDir + "/battery_low.svg";
                    else if (percent < 80)
                        bat.source = Env.assetsDir + "/battery_mid.svg";
                    else
                        bat.source = Env.assetsDir + "/battery_full.svg";
                }
            }
        }
        Image {
            id: bat
            anchors.right: parent.right
            source: Env.assetsDir + "/battery_full.svg"
            sourceSize.width: 200
            opacity: 0.7
        }

        Text {
            id: timetxt
            text: Qt.formatTime(new Date(), "hh:mm")
            color: "#ffffff"
            font.pointSize: 64
            font.family: "BigBlueTermPlus Nerd Font"
            font.bold: true
            font.italic: true
            Layout.fillHeight: true
            verticalAlignment: Text.AlignVCenter
            opacity: 0.85
        }

        Timer {
            interval: 1000
            running: true
            repeat: true
            onTriggered: timetxt.text = Qt.formatTime(new Date(), "hh:mm")
        }

        Timer {
            interval: 15000
            running: true
            repeat: true
            triggeredOnStart: true
            onTriggered: batProc.running = true
        }
    }

    AnimatedImage {
        id: reflection
        source: Env.assetsDir + "/reflection.gif"
        anchors.fill: parent
        fillMode: Image.FillMode.PreserveAspectCrop
        visible: false
    }
    MultiEffect {
        source: reflection
        anchors.fill: reflection
        blurEnabled: true
        blur: 1
        blurMultiplier: 5
        opacity: 0.15
        contrast: 0.1
        saturation: -0.5
        brightness: -0.5
    }
}
