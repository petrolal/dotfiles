// Osd.tsx --- center OSD / tape-deck telemetry strip: a blinking "● REC"
// tally, an "SP" speed badge, a glowing green tape counter (INDEX,
// HH:MM:SS), and a cyan "TRK:" RAM-usage readout -- the on-screen display
// band of a VCR deck, not tied to any one media source.
import { Variable } from "astal"

export default function Osd() {
    const time = Variable("").poll(1000, "date '+%H:%M:%S'")

    // GTK3 CSS can't reliably animate arbitrary widgets, so the tally
    // light blinks by toggling a class from JS instead of @keyframes.
    let lit = true
    const recLit = Variable(true).poll(600, () => (lit = !lit))

    const ram = Variable(0).poll(
        3000,
        "sh -c 'free | awk \"/Mem:/{printf \\\"%d\\\", \\$3/\\$2*100}\"'",
        (out, prev) => {
            const n = parseInt(out, 10)
            return Number.isNaN(n) ? prev : n
        },
    )

    return <box className="Osd" spacing={10}>
        <box className="rec" spacing={3}>
            <label
                className={recLit().as(lit => `rec-dot ${lit ? "lit" : ""}`)}
                label="●"
            />
            <label className="rec-text" label="REC" />
        </box>
        <label className="speed" label="SP" />
        <box className="index" spacing={4}>
            <label className="index-label" label="INDEX" />
            <label className="counter" label={time()} />
        </box>
        <box className="trk" spacing={4}>
            <label className="trk-label" label="TRK:" />
            <label className="trk-value" label={ram().as(p => `${p}%`)} />
        </box>
    </box>
}
