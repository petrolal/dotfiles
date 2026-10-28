import QtQuick
import QtQuick.Effects
import Quickshell.Io

Rectangle {
    id: root
    anchors.fill: parent
    color: "black"

    Process {
        id: pauseCmd
    }

    onVisibleChanged: () => {
        if (visible) {
            introSound.running = true;
            pauseCmd.command = ["rmpc", "pause"];
            pauseCmd.running = true;
        } else {
            introSound.running = false;
            music.running = false;
            pauseCmd.command = ["rmpc", "unpause"];
            pauseCmd.running = true;
        }
    }

    Process {
        id: introSound
        command: ["play", "-v", "4", "/home/petrolal/dotfiles/astroland/assets/hold_intro.wav"]
        running: true
        onExited: music.running = root.visible
    }
    Process {
        id: music
        command: ["ffplay", "-af", "volume=4", "-nodisp", "-loop", "0", "/home/petrolal/dotfiles/astroland/assets/hold_music.wav"]
        onStarted: dvdEffect.visible = true
        running: false
    }

    Image {
        id: dvd
        source: "/home/petrolal/dotfiles/astroland/assets/dvd.webp"
        x: 0
        y: 0
        fillMode: Image.PreserveAspectFit
        visible: false
    }
    MultiEffect {
        id: dvdEffect
        source: dvd
        anchors.fill: dvd
        colorization: 0.5
        colorizationColor: "blue"
        scale: 2
        visible: false
    }

    FrameAnimation {
        property int dirx: 1
        property int diry: 1

        running: true
        onTriggered: {
            dvd.x += 300 * frameTime * dirx;
            dvd.y += 300 * frameTime * diry;

            if ((dvd.x + dvd.width) >= root.width) {
                dirx = -1;
                newColor();
            }
            if (dvd.x <= 0) {
                dirx = 1;
                newColor();
            }

            if ((dvd.y + dvd.height) >= root.height) {
                diry = -1;
                newColor();
            }

            if (dvd.y <= 0) {
                diry = 1;
                newColor();
            }
        }

        function newColor() {
            var colors = ["red", "orange", "yellow", "green", "blue", "purple", "violet"];
            dvdEffect.colorizationColor = colors[Math.floor(Math.random() * colors.length)];
        }
    }
}
