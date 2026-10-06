//@ pragma UseQApplication
//@ pragma Env QS_NO_RELOAD_POPUP=1
//@ pragma Env QT_QUICK_CONTROLS_STYLE=Basic
//@ pragma Env QT_QUICK_FLICKABLE_WHEEL_DECELERATION=10000

/* NOTE: CHANGE THESE IF YOU WANT TO USE A DIFFERENT ICON THEME:*/
//@ pragma IconTheme imp98
//@ pragma Env QS_ICON_THEME=imp98

import QtQuick
import Quickshell

import "taskbar" as Taskbar
import "popups" as Popups

Scope {
    id: root
    Taskbar.Bar {}

    FloatingWindow {
        id: settingsWindow
        title: "imp98SettingsWindow"
        reloadableId: "imp98SettingsWindow"
        visible: Config.openSettingsWindow
        Popups.PopupWindowFrame {
            id: settingsWindowFrame
            windowTitle: "Settings"
            windowTitleIcon: "\ue8b8"
            windowTitleDecorationWidth: (settingsWindow.width / 2) - 70

            anchors.leftMargin: -1
            anchors.bottomMargin: -1
            anchors.rightMargin: -1
            Item {
                id: content
                anchors.fill: settingsWindowFrame
                anchors.margins: 18
                anchors.topMargin: 20 + 18
                clip: true
                Rectangle {
                    anchors.left: parent.left
                    anchors.right: parent.right
                    color: Config.colors.highlight
                    border.width: 1
                    border.color: Config.colors.outline
                    height: 148
                    Text {
                        anchors.fill: parent
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        font.family: Config.fontMonacoName
                        font.pixelSize: 28
                        text: "Linux imp98 " + Config.settings.version
                    }
                    Text {
                        anchors.fill: parent
                        anchors.bottomMargin: 16
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignBottom
                        font.family: Config.fontMonacoName
                        font.pixelSize: 12
                        text: "Version " + Config.settings.version + " is very early and does not yet have a proper settings menu.\nPlease look forward for future releases on github ~ diinki"
                    }
                }
            }
        }
        onClosed: {
            Config.openSettingsWindow = false;
        }
    }
}
