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

    // Use the wlroots specific layer property to ensure it displays over
    // fullscreen windows.
    WlrLayershell.layer: WlrLayer.Overlay

    property alias showInfo: component.showInfo

    InfoOverlayComponent {
        id: component
    }
}
