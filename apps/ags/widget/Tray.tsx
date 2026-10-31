// Tray.tsx --- system tray, rendered as small icons on a "VHS static"
// strip at the end of the bar.
import { bind } from "astal"
import AstalTray from "gi://AstalTray"

export default function Tray() {
    const tray = AstalTray.get_default()

    return <box className="Tray">
        {bind(tray, "items").as(items => items.map(item => (
            <button
                className="tray-item"
                tooltipMarkup={bind(item, "tooltipMarkup")}
                onClick={(_btn, event) => item.activate(event)}
            >
                <icon gicon={bind(item, "gicon")} />
            </button>
        )))}
    </box>
}
