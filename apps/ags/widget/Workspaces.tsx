// Workspaces.tsx --- Hyprland workspace indicator, styled like a VCR deck's
// program-position selector: a "TAPE:" chassis label followed by
// mechanical-looking [01]..[0N] buttons. ".active" is the focused
// workspace (currently loaded program), ".occupied" is any workspace
// holding client windows (a tape "side" that isn't blank).
import { bind } from "astal"
import AstalHyprland from "gi://AstalHyprland"
import { roomManager } from "./RoomManager"

export default function Workspaces() {
    const hypr = AstalHyprland.get_default()

    return <box className="Workspaces" spacing={4}>
        <button
            className="tape-btn"
            onClicked={() => roomManager.travel("terminal")}
        >
            <label className="tape-label" label="TAPE:" />
        </button>
        {bind(hypr, "workspaces").as(workspaces =>
            workspaces
                .slice()
                .sort((a, b) => a.id - b.id)
                .map(ws => (
                    <button
                        className={bind(hypr, "focusedWorkspace").as(focused => {
                            const active = focused?.id === ws.id
                            const occupied = ws.clients.length > 0
                            return `channel-btn ${active ? "active" : ""} ${occupied ? "occupied" : ""}`
                        })}
                        onClicked={() => ws.focus()}
                    >
                        <label label={`[${String(ws.id).padStart(2, "0")}]`} />
                    </button>
                )))}
    </box>
}
