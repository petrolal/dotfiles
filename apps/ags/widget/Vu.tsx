// Vu.tsx --- passive audio VU meter: the default speaker's volume (via
// AstalWp) rendered as a row of segment ticks, colored green/yellow/red by
// how "hot" the signal is, like a tape deck's analog level display.
// Read-only here -- the HUD popup (Hud.tsx) owns the interactive fader.
import { bind } from "astal"
import AstalWp from "gi://AstalWp"

const SEGMENTS = 10

function tickClass(index: number, level: number, muted: boolean) {
    const on = index < Math.round(level * SEGMENTS)
    const zone = index < 6 ? "lo" : index < 8 ? "mid" : "hi"
    return `vu-tick ${zone} ${on && !muted ? "on" : ""}`
}

export default function Vu() {
    const audio = AstalWp.get_default()?.audio
    const speaker = audio?.defaultSpeaker

    if (!speaker)
        return <box className="Vu" visible={false} />

    return <box className="Vu" spacing={1}>
        <label className="vu-label" label="VU" />
        {Array.from({ length: SEGMENTS }, (_, i) => (
            <box
                className={bind(speaker, "volume").as(v =>
                    tickClass(i, v, speaker.mute))}
            />
        ))}
    </box>
}
