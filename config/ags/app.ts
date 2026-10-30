import { App } from "astal/gtk3"
import style from "./style.scss"
import Bar from "./widget/Bar"
import Hud from "./widget/Hud"

App.start({
    instanceName: "bar",
    css: style,
    main() {
        const monitors = App.get_monitors()
        monitors.map(Bar)
        // Hud is a single popup (toggled by name, "vhs-hud") -- one
        // instance total, anchored to the first monitor, not one per
        // monitor like Bar.
        if (monitors[0])
            Hud(monitors[0])
    },
})
