// RoomManager.ts --- 3D Retro Room navigation and laptop fold controller
// License: GPL-3.0-or-later
import GLib from "gi://GLib?version=2.0"
import { Variable } from "astal"

export class RoomManager {
    private static instance: RoomManager | null = null
    public readonly isFolded = Variable<boolean>(false)
    public readonly currentAnchor = Variable<string>("home")

    private timerId: number | null = null
    private readonly INACTIVITY_MS = 30000 // 30 seconds

    private constructor() {}

    public static get(): RoomManager {
        if (!RoomManager.instance) {
            RoomManager.instance = new RoomManager()
        }
        return RoomManager.instance
    }

    public travel(anchor: "home" | "terminal" | "music"): void {
        this.resetInactivityTimer()

        if (anchor === "home") {
            // Unfold bar and travel home
            this.isFolded.set(false)
            this.currentAnchor.set("home")
            GLib.spawn_command_line_async("imp room reset")
            return
        }

        // Fold bar down like a laptop lid before travel
        this.isFolded.set(true)
        this.currentAnchor.set(anchor)

        // Give the 250ms CSS perspective fold 80ms lead before camera sweeps
        GLib.timeout_add(GLib.PRIORITY_DEFAULT, 80, () => {
            GLib.spawn_command_line_async(`imp room travel --anchor ${anchor}`)
            return GLib.SOURCE_REMOVE
        })

        // Start 30s inactivity auto-reset timer
        this.startInactivityTimer()
    }

    public reset(): void {
        this.clearTimer()
        this.isFolded.set(false)
        this.currentAnchor.set("home")
        GLib.spawn_command_line_async("imp room reset")
    }

    private startInactivityTimer(): void {
        this.clearTimer()
        this.timerId = GLib.timeout_add(GLib.PRIORITY_DEFAULT, this.INACTIVITY_MS, () => {
            this.reset()
            return GLib.SOURCE_REMOVE
        })
    }

    private resetInactivityTimer(): void {
        if (this.timerId !== null) {
            this.startInactivityTimer()
        }
    }

    private clearTimer(): void {
        if (this.timerId !== null) {
            GLib.source_remove(this.timerId)
            this.timerId = null
        }
    }
}

export const roomManager = RoomManager.get()
