import QtQuick
import ".."

Text {
    text: Time.time
    color: Config.colors.text
    font.pixelSize: Config.settings.bar.fontSize
    font.family: Config.fontMonacoName
    horizontalAlignment: Text.AlignHCenter
    verticalAlignment: Text.AlignVCenter
}
