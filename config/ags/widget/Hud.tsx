// Hud.tsx --- cassette-inspection HUD: a togglable popup (window name
// "vhs-hud", toggled from Media.tsx via App.toggleWindow) showing a tape
// spool mockup, a scrubbable position slider, a volume fader, a
// "TRACKING" RAM-load readout, and a small tape metadata matrix. Like
// Osd.tsx's "REC"/"SP" tally, STATUS/FORMAT/CODEC below are flavor text
// for the VCR fiction, not real diagnostics -- FORMAT/CODEC aren't
// knowable from MPRIS, so they stay decorative; STATUS is real.
import { bind, Variable } from "astal"
import { App, Astal, Gdk, Gtk } from "astal/gtk3"
import AstalMpris from "gi://AstalMpris"
import AstalWp from "gi://AstalWp"

function fmt(seconds: number) {
    if (!Number.isFinite(seconds) || seconds < 0)
        return "--:--"
    const m = Math.floor(seconds / 60)
    const s = Math.floor(seconds % 60)
    return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
}

export default function Hud(gdkmonitor: Gdk.Monitor) {
    const { TOP } = Astal.WindowAnchor
    const mpris = AstalMpris.Mpris.get_default()
    const { PLAYING } = AstalMpris.PlaybackStatus
    const speaker = AstalWp.get_default()?.audio?.defaultSpeaker

    const player = Variable(mpris.players[0] ?? null)
    mpris.connect("items-changed", () => player.set(mpris.players[0] ?? null))

    // Position/length aren't notified by MPRIS, so the slider and spool
    // animation are driven off the same 1s tick as Media.tsx's VFD text.
    const tick = Variable(0).poll(1000, () => Date.now())

    const ram = Variable(0).poll(
        3000,
        "sh -c 'free | awk \"/Mem:/{printf \\\"%d\\\", \\$3/\\$2*100}\"'",
        (out, prev) => {
            const n = parseInt(out, 10)
            return Number.isNaN(n) ? prev : n
        },
    )

    return <window
        name="vhs-hud"
        className="Hud"
        gdkmonitor={gdkmonitor}
        anchor={TOP}
        exclusivity={Astal.Exclusivity.NORMAL}
        keymode={Astal.Keymode.ON_DEMAND}
        visible={false}
        application={App}
    >
        <box className="shell" vertical spacing={10}>
            <box className="spools" halign={Gtk.Align.CENTER}>
                <label
                    className="spool-art"
                    label={tick(() => {
                        const p = player.get()
                        const spinning = p?.playbackStatus === PLAYING
                        return spinning
                            ? "(◉) ============ (◎)"
                            : "(◎) ============ (◎)"
                    })}
                />
            </box>

            <box className="scrub" spacing={6}>
                <label label={tick(() => fmt(player.get()?.position ?? -1))} />
                <slider
                    hexpand
                    min={0}
                    max={player().as(p => Math.max(p?.length ?? 0, 1))}
                    value={tick(() => player.get()?.position ?? 0)}
                    onDragged={self => player.get()?.set_position(self.value)}
                />
                <label label={tick(() => fmt(player.get()?.length ?? -1))} />
            </box>

            <box className="faders" spacing={16}>
                <box className="fader" vertical spacing={4}>
                    <label className="fader-label" label="VOLUME" />
                    <slider
                        vertical
                        visible={!!speaker}
                        min={0}
                        max={1}
                        value={speaker ? bind(speaker, "volume") : 0}
                        onDragged={self => speaker && (speaker.volume = self.value)}
                    />
                </box>
                <box className="fader" vertical spacing={4}>
                    <label className="fader-label" label="TRACKING" />
                    <slider
                        vertical
                        sensitive={false}
                        min={0}
                        max={100}
                        value={ram()}
                    />
                    <label className="fader-value" label={ram().as(p => `${p}%`)} />
                </box>
            </box>

            <box className="matrix" halign={Gtk.Align.CENTER} spacing={14}>
                <label label={tick(() => {
                    const p = player.get()
                    const status = !p ? "NO TAPE" : p.playbackStatus === PLAYING ? "PLAYING" : "STOPPED"
                    return `STATUS: ${status}`
                })} />
                <label label="FORMAT: SP" />
                <label label="CODEC: --" />
                <label label={player().as(p => `ALBUM: ${p?.album || "--"}`)} />
            </box>
        </box>
    </window>
}
