import QtQuick
import Quickshell
import Quickshell.Wayland
import "./InfoOverlayComponent.qml"

PanelWindow {
    anchors {
        right: true
        left: true
        top: true
        bottom: true
    }
    exclusionMode: ExclusionMode.Ignore

    color: "transparent"

    // Give the window an empty click mask so all clicks pass through it.
    mask: Region {}

    // Use Bottom layer so it stays beneath tiled windows and doesn't obstruct reading.
    WlrLayershell.layer: WlrLayer.Bottom

    property alias showInfo: component.showInfo

    InfoOverlayComponent {
        id: component
    }
}
