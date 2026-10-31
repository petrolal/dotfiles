// Bar.tsx --- top bar styled like a VCR deck's front panel. Edit the
// widgets below independently -- each one is its own file in this
// directory (Workspaces.tsx, Media.tsx, Osd.tsx, Vu.tsx, Battery.tsx,
// Tray.tsx). Hud.tsx is a separate popup window, toggled from Media.tsx.
import { App, Astal, Gdk } from "astal/gtk3"
import { bind } from "astal"
import Workspaces from "./Workspaces"
import Media from "./Media"
import Osd from "./Osd"
import Vu from "./Vu"
import Battery from "./Battery"
import Tray from "./Tray"
import { roomManager } from "./RoomManager"

export default function Bar(gdkmonitor: Gdk.Monitor) {
    const { TOP, LEFT, RIGHT } = Astal.WindowAnchor

    // <centerbox> lays its children out start/center/end by position, not
    // by any alignment prop, so each slot below is just a plain <box>.
    return <window
        className="Bar"
        gdkmonitor={gdkmonitor}
        exclusivity={Astal.Exclusivity.EXCLUSIVE}
        anchor={TOP | LEFT | RIGHT}
        application={App}
    >
        <centerbox className={bind(roomManager.isFolded).as(folded => folded ? "chassis-bar folded" : "chassis-bar")}>
            <box className="start" spacing={8}>
                <button
                    className="logo-btn"
                    onClicked={() => roomManager.reset()}
                >
                    <label className="logo" label="📼 SHUT THE FUCK UP!" />
                </button>
                <Workspaces />
            </box>
            <Media />
            <box className="end" spacing={10}>
                <Osd />
                <Vu />
                <Tray />
                <Battery />
            </box>
        </centerbox>
    </window>
}
