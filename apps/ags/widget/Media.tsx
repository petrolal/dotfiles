// Media.tsx --- VCR-deck media transport: a VFD-style track readout
// ([MM:SS] + "Artist - Title", or "NO TAPE INSERTED" with nothing
// playing) plus mechanical REW/PLAY-PAUSE/STOP/FF push-buttons and an
// explicit TAPE/HUD button, all driven by the first available MPRIS
// player (gi://AstalMpris). Clicking the VFD screen itself also opens the
// cassette-inspection HUD (Hud.tsx) via App.toggleWindow("vhs-hud").
import { Variable } from "astal"
import { App } from "astal/gtk3"
import AstalMpris from "gi://AstalMpris"

function fmt(seconds: number) {
    if (!Number.isFinite(seconds) || seconds < 0)
        return "--:--"
    const m = Math.floor(seconds / 60)
    const s = Math.floor(seconds % 60)
    return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
}

export default function Media() {
    const mpris = AstalMpris.Mpris.get_default()
    const { PLAYING } = AstalMpris.PlaybackStatus

    const player = Variable(mpris.players[0] ?? null)
    mpris.connect("items-changed", () => {
        player.set(mpris.players[0] ?? null)
    })

    // AstalMpris.Player.position isn't itself bindable/notified on a
    // timer, so poll it the same way the other widgets poll shell
    // commands -- just reading the live property instead of spawning one.
    const tick = Variable(0).poll(1000, () => Date.now())

    return <box className="Media">
        <button className="vfd" onClicked={() => App.toggleWindow("vhs-hud")}>
            <box spacing={6}>
                {player().as(p => {
                    if (!p)
                        return <label className="vfd-text" label="NO TAPE INSERTED" />

                    return <label
                        className="vfd-text"
                        label={tick(() => {
                            const pos = fmt(p.position)
                            const artist = p.artist || ""
                            const title = p.title || "NO TAPE INSERTED"
                            const info = artist ? `${artist} - ${title}` : title
                            return `[${pos}] ${info}`
                        })}
                    />
                })}
            </box>
        </button>
        <box className="transport" spacing={2}>
            <button
                className="deck-btn"
                sensitive={player().as(p => !!p?.canGoPrevious)}
                onClicked={() => player.get()?.previous()}
            >
                <label label="◀◀" />
            </button>
            <button
                className={player().as(p =>
                    `deck-btn play ${p?.playbackStatus === PLAYING ? "is-playing" : ""}`)}
                sensitive={player().as(p => !!p?.canControl)}
                onClicked={() => player.get()?.play_pause()}
            >
                <label label={player().as(p =>
                    p?.playbackStatus === PLAYING ? "⏸" : "▶")} />
            </button>
            <button
                className="deck-btn"
                sensitive={player().as(p => !!p?.canControl)}
                onClicked={() => player.get()?.stop()}
            >
                <label label="■" />
            </button>
            <button
                className="deck-btn"
                sensitive={player().as(p => !!p?.canGoNext)}
                onClicked={() => player.get()?.next()}
            >
                <label label="▶▶" />
            </button>
            <button className="deck-btn hud-btn" onClicked={() => App.toggleWindow("vhs-hud")}>
                <label label="📼" />
            </button>
        </box>
    </box>
}
