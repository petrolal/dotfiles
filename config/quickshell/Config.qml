pragma Singleton
import QtQuick
import Quickshell
import Quickshell.Io

Singleton {
    id: root

    // Loaded once here so every file can reach them via Config.iconFontName /
    // Config.fontMonacoName / Config.fontCharcoalName — a FontLoader's `id`
    // doesn't cross file boundaries in QML, so these can't live in shell.qml
    // despite being referenced from almost every popup/taskbar file.
    FontLoader {
        id: iconFontLoader
        source: "fonts/MaterialSymbolsSharp_Filled_36pt-Regular.ttf"
    }
    FontLoader {
        id: fontMonacoLoader
        source: "fonts/Monaco.ttf"
    }
    FontLoader {
        id: fontCharcoalLoader
        source: "fonts/Charcoal.ttf"
    }
    property string iconFontName: iconFontLoader.name
    property string fontMonacoName: fontMonacoLoader.name
    property string fontCharcoalName: fontCharcoalLoader.name

    //*=======================================================================*/
    // READ THIS NOTE:
    // Simply add to this list in order to create your
    // own color schemes, they will automatically show up in the theme picker.
    property var colors: themes[themes[settings.currentTheme] == null ? 'default' : settings.currentTheme]
    property var themes: {
        "abyssal": {
            "base": "#D6CBBB",
            "shadow": "#766E63",
            "highlight": "#E3DAC9",
            "urgent": "#9E2A2B",
            "accent": "#9E2A2B",
            "text": "#332E28",
            "outline": "#4A3B3A",
            "outlineGradientFade": "#766E63"
        },
        "default": {
            "base": "#d8d8d8",
            "shadow": "#9b9b9b",
            "highlight": "#efefef",
            "urgent": "#ff723e",
            "accent": "#207874",
            "text": "#000000",
            "outline": "#000000",
            "outlineGradientFade": "#161616"
        },
        "yorha": {
            "base": "#d9caba",
            "shadow": "#baafa1",
            "highlight": "#f0e2d3",
            "urgent": "#ff854c",
            "accent": "#626335",
            "text": "#3e3d38",
            "outline": "#3d3d39",
            "outlineGradientFade": "#5b5b45"
        },
        "cherry": {
            "base": "#f4c9ef",
            "shadow": "#c7a4cc",
            "highlight": "#f9d0f7",
            "urgent": "#ff936c",
            "accent": "#9E2A2B",
            "text": "#321d32",
            "outline": "#20091d",
            "outlineGradientFade": "#3e233e"
        },
        "indigo": {
            "base": "#bac4e6",
            "shadow": "#7e8bad",
            "highlight": "#d0def9",
            "urgent": "#e83939",
            "accent": "#3e7c99",
            "text": "#0d0d19",
            "outline": "#1a2135",
            "outlineGradientFade": "#223143"
        },
        "gleep": {
            "base": "#bae6c5",
            "shadow": "#93c48c",
            "highlight": "#ccf9e7",
            "urgent": "#ff7559",
            "accent": "#3e9949",
            "text": "#0d1913",
            "outline": "#21351a",
            "outlineGradientFade": "#284223"
        },
        "imp95": {
            "base": "#2F2F2F",
            "shadow": "#1E1E1E",
            "highlight": "#3C3C3C",
            "urgent": "#9E2A2B",
            "accent": "#9E2A2B",
            "text": "#FFFFFF",
            "outline": "#000000",
            "outlineGradientFade": "#555555"
        }
    }

    enum SystemPopup {
        Startmenu,
        ThemePicker,
        AppLauncher,
        None
    }

    property bool openSettingsWindow: false

    property alias settings: settingsJsonAdapter.settings
    FileView {
        path: Qt.resolvedUrl("./settings.json")
        // when changes are made on disk, reload the file's content
        watchChanges: true
        onFileChanged: reload()
        // when changes are made to properties in the adapter, save them
        onAdapterUpdated: writeAdapter()

        onLoadFailed: error => {
            if (error == FileViewError.FileNotFound) {
                writeAdapter();
            }
        }

        JsonAdapter {
            id: settingsJsonAdapter
            property JsonObject settings: JsonObject {
                property string version: "0.1"
                property bool militaryTimeClockFormat: true
                property string systemProfileImageSource: Quickshell.env("HOME") + "/Pictures/system_profile_picture.png"
                property string currentTheme: "default"
                property bool setWallpaperToThemeWallpaper: true
                property JsonObject execCommands: JsonObject {
                    property string terminal: "xfce4-terminal"
                    property string files: "nemo"
                }
                property JsonObject systemDetails: JsonObject {
                    property string osName: "Linux Distro"
                    property string osVersion: "Distro Version"
                    property string ram: "Ram"
                    property string cpu: "CPU Name"
                    property string gpu: "GPU Name"
                }
                property JsonObject bar: JsonObject {
                    property int fontSize: 12
                    property int trayIconSize: 16
                    property bool monochromeTrayIcons: true
                }

                onCurrentThemeChanged: {
                    console.info("Updated theme to: " + currentTheme);
                }
            }
        }
    }

    // Populate systemDetails from fastfetch once at startup. Falls back to
    // whatever is already in settings.json if fastfetch is missing or its
    // output can't be parsed.
    Process {
        id: systemDetailsProbe
        command: ["fastfetch", "--format", "json", "-s", "CPU:GPU:Memory:OS"]
        stdout: StdioCollector {
            onStreamFinished: {
                try {
                    const modules = JSON.parse(text);
                    for (const mod of modules) {
                        switch (mod.type) {
                        case "CPU":
                            root.settings.systemDetails.cpu = mod.result.cpu;
                            break;
                        case "GPU":
                            root.settings.systemDetails.gpu = mod.result[0].name;
                            break;
                        case "Memory":
                            root.settings.systemDetails.ram = (mod.result.total / (1024 * 1024 * 1024)).toFixed(1) + " GB";
                            break;
                        case "OS":
                            root.settings.systemDetails.osName = mod.result.name;
                            root.settings.systemDetails.osVersion = mod.result.version;
                            break;
                        }
                    }
                } catch (e) {
                    console.warn("Config: failed to parse fastfetch output, keeping existing systemDetails", e);
                }
            }
        }
    }

    Component.onCompleted: systemDetailsProbe.running = true
}
