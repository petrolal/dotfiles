import QtQuick
import QtQuick3D

Effect {
    id: root

    Pass {
        id: vhsPass
        shaders: [
            Shader {
                stage: Shader.Fragment
                shader: "vhs_effect.frag"
            }
        ]
    }

    passes: [ vhsPass ]
}
