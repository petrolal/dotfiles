import QtQuick
import QtQuick.Effects
import QtQuick.Layouts
import QtQuick.Controls.Fusion
import Quickshell.Io
import "./InfoOverlayComponent.qml"

Rectangle {
    id: root
    required property LockContext context

    color: "#000"

    InfoOverlayComponent {}

    Rectangle {
        id: container
        color: "transparent"
        anchors.fill: parent
        visible: false

        ColumnLayout {
            anchors {
                horizontalCenter: parent.horizontalCenter
                top: parent.verticalCenter
            }

            RowLayout {
                Text {
                    text: "password:"
                    color: "white"
                    font.pointSize: 32
                    font.family: "monospace"
                    font.bold: true
                }
                TextField {
                    id: passwordBox

                    implicitWidth: 400
                    font.pointSize: 32
                    font.family: "monospace"
                    font.bold: true

                    padding: 10

                    focus: true
                    enabled: !root.context.unlockInProgress
                    echoMode: TextInput.Password
                    passwordCharacter: "*"
                    inputMethodHints: Qt.ImhSensitiveData

                    // Update the text in the context when the text in the box changes.
                    onTextChanged: root.context.currentText = this.text

                    // Try to unlock when enter is pressed.
                    onAccepted: root.context.tryUnlock()

                    color: "white"
                    cursorDelegate: Rectangle {
                        id: cursor
                        width: 20
                        color: "white"
                        SequentialAnimation {
                            loops: Animation.Infinite
                            running: true
                            PropertyAction {
                                target: cursor
                                property: "visible"
                                value: true
                            }
                            PauseAnimation {
                                duration: 500
                            }
                            PropertyAction {
                                target: cursor
                                property: "visible"
                                value: false
                            }
                            PauseAnimation {
                                duration: 500
                            }
                        }
                    }
                    background: Rectangle {
                        color: "transparent"
                    }

                    // This makes sure multiple monitors have the same text.
                    Connections {
                        target: root.context

                        function onCurrentTextChanged() {
                            passwordBox.text = root.context.currentText;
                        }
                    }
                }
            }
        }

        Image {
            id: m
            source: "/home/petrolal/dotfiles/astroland/assets/m_for_masterful.png"
            anchors.left: parent.left
            anchors.bottom: parent.bottom
            anchors.margins: {
                left: 30;
                bottom: 30;
            }
            width: 150
            fillMode: Image.PreserveAspectFit
        }
        Image {
            source: "/home/petrolal/dotfiles/astroland/assets/warning_label.png"
            anchors.left: m.right
            anchors.bottom: parent.bottom
            anchors.margins: {
                left: 30;
                bottom: 30;
            }
            width: 445
            fillMode: Image.PreserveAspectFit
        }
        Image {
            source: "/home/petrolal/dotfiles/astroland/assets/barcode.png"
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.margins: {
                right: 30;
                bottom: 30;
            }
            width: 300
            fillMode: Image.PreserveAspectFit
        }
    }

    Image {
        id: scrcpImage
        anchors.fill: parent
        source: "/tmp/lock_screencopy.jpeg"
        visible: false
    }
    MultiEffect {
        id: scrcp
        anchors.centerIn: parent
        width: root.width > 0 ? root.width : 1920
        height: root.height > 0 ? root.height : 1080
        source: scrcpImage
        brightness: 0
    }

    Process {
        id: tvOffSound
        command: ["play", "--volume", "3", "/home/petrolal/dotfiles/astroland/assets/tv_off.wav"]
        running: true
        onStarted: tvOffAnim.running = true
    }

    SequentialAnimation {
        id: tvOffAnim
        running: false

        ParallelAnimation {
            // v squash
            ParallelAnimation {
                NumberAnimation {
                    target: scrcp
                    property: "brightness"
                    to: 1
                    duration: 1000
                    easing.type: Easing.OutExpo
                }
                NumberAnimation {
                    target: scrcp
                    property: "height"
                    to: 10
                    duration: 500
                    easing.type: Easing.OutExpo
                }
                SequentialAnimation {
                    PauseAnimation {
                        duration: 300
                    }
                    // h squash
                    NumberAnimation {
                        target: scrcp
                        property: "width"
                        to: 00
                        duration: 300
                        easing.type: Easing.OutExpo
                    }
                    PropertyAction {
                        target: scrcp
                        property: "visible"
                        value: false
                    }
                }
            }
        }

        PropertyAction {
            target: flickerSound
            property: "running"
            value: true
        }
    }

    Process {
        id: flickerSound
        command: ["play", "--volume", "3", "/home/petrolal/dotfiles/astroland/assets/lights_flicker.wav"]
        running: false
        onStarted: flickerAnim.running = true
        onExited: noiseSound.running = true
    }
    Process {
        id: noiseSound
        command: ["ffplay", "-nodisp", "-loop", "0", "-af", "volume=3", "/home/petrolal/dotfiles/astroland/assets/lights_noise.wav"]
        running: false
    }

    SequentialAnimation {
        id: flickerAnim

        // flicker in
        PauseAnimation {
            duration: 200
        }

        PropertyAction {
            target: container
            property: "visible"
            value: true
        }
        PauseAnimation {
            duration: 100
        }
        PropertyAction {
            target: container
            property: "visible"
            value: false
        }
        PauseAnimation {
            duration: 200
        }
        PropertyAction {
            target: container
            property: "visible"
            value: true
        }
    }

    Connections {
        target: root.context

        function onUnlocked() {
            tvOffSound.running = false;
            flickerSound.running = false;
            noiseSound.running = false;
        }
    }
}
