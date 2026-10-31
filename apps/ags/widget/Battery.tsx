// Battery.tsx --- reuses the same battery_{full,mid,low}.svg icons and
// /sys/class/power_supply polling approach as
// apps/quickshell/overlay/InfoOverlayComponent.qml, so the two overlays
// stay visually consistent. No AstalBattery typelib is packaged (see
// nixos/modules/hyprland.nix), hence the raw sysfs read instead of a
// gi://Astal* import.
import { Variable } from "astal"
import GLib from "gi://GLib?version=2.0"

const ASSET_DIR = `${GLib.getenv("HOME")}/dotfiles/assets`

function iconFor(percent: number) {
    if (percent < 40) return `${ASSET_DIR}/battery_low.svg`
    if (percent < 80) return `${ASSET_DIR}/battery_mid.svg`
    return `${ASSET_DIR}/battery_full.svg`
}

const POWER_SUPPLY_DIR = "/sys/class/power_supply"

function readCapacity(prev: number): number {
    try {
        const dir = GLib.Dir.open(POWER_SUPPLY_DIR, 0)
        let batteryName: string | null
        while ((batteryName = dir.read_name()) !== null) {
            if (!batteryName.startsWith("BAT")) continue
            const [ok, bytes] = GLib.file_get_contents(`${POWER_SUPPLY_DIR}/${batteryName}/capacity`)
            if (!ok) continue
            const n = parseInt(new TextDecoder().decode(bytes), 10)
            return Number.isNaN(n) ? prev : n
        }
        return prev
    } catch {
        return prev
    }
}

export default function Battery() {
    const percent = Variable(100).poll(15000, readCapacity)

    return <box className="Battery" visible={percent().as(p => p >= 0)}>
        <icon icon={percent().as(iconFor)} />
        <label label={percent().as(p => `PWR ${p}%`)} />
    </box>
}
