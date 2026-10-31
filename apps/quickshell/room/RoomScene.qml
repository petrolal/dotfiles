import QtQuick
import QtQuick3D
import RetroRoom 1.0

Item {
    id: root

    property alias controller: sceneController
    property alias registry: anchorRegistry
    property alias cameraRig: cameraRig
    property bool vhsShaderEnabled: true

    readonly property string assetsRoot: Qt.resolvedUrl("assets/")

    // Laptop clamshell lid angle: 0 (closed) to -110 (open) synchronized with camera travel
    readonly property bool isTerminalWorkspace: (sceneController.currentAnchor === "terminal" || sceneController.targetAnchor === "terminal")
    property real laptopLidAngle: isTerminalWorkspace ? -110.0 : 0.0

    Behavior on laptopLidAngle {
        NumberAnimation {
            duration: cameraRig.duration
            easing.type: Easing.InOutCubic
        }
    }

    SceneAnchorRegistry {
        id: anchorRegistry
    }

    CameraRig {
        id: cameraRig
        camera: sceneCamera
        duration: 400
    }

    RoomSceneController {
        id: sceneController
        cameraRig: cameraRig
        registry: anchorRegistry
    }

    VhsEffect {
        id: vhsEffect
    }

    View3D {
        id: view3d
        anchors.fill: parent

        environment: SceneEnvironment {
            id: env
            clearColor: "#050505"
            backgroundMode: SceneEnvironment.Color
            antialiasingMode: SceneEnvironment.NoAA // Raw retro PS1 pixel edges
            effects: root.vhsShaderEnabled ? [ vhsEffect ] : []
        }

        PerspectiveCamera {
            id: sceneCamera
            position: Qt.vector3d(0, 180, 320)
            eulerRotation: Qt.vector3d(-20, 0, 0)
            clipNear: 1.0
            clipFar: 5000.0
            fieldOfView: 60.0
        }

        // =====================================================================
        // CAMERA ANCHORS
        // =====================================================================
        Node {
            id: anchorHome
            position: Qt.vector3d(0, 180, 320)
            eulerRotation: Qt.vector3d(-20, 0, 0)
        }

        Node {
            id: anchorTerminal
            position: Qt.vector3d(85, 95, 45)
            eulerRotation: Qt.vector3d(0, -15, 0)
        }

        Node {
            id: anchorMusic
            position: Qt.vector3d(90, 60, 120)
            eulerRotation: Qt.vector3d(-10, -45, 0)
        }

        Component.onCompleted: {
            anchorRegistry.registerAnchor("home", anchorHome);
            anchorRegistry.registerAnchor("terminal", anchorTerminal);
            anchorRegistry.registerAnchor("music", anchorMusic);
        }

        // =====================================================================
        // LIGHTING (Chiaroscuro & Hard Cel-Shaded Contrast)
        // =====================================================================

        // Very low ambient/fill light for pitch-black shadowed corners
        DirectionalLight {
            eulerRotation: Qt.vector3d(-45, 30, 0)
            color: "#1a1a24"
            ambientColor: "#08080c"
            brightness: 0.8
        }

        // CRT Television Cold Bluish-White Light (#80C8FF)
        PointLight {
            id: tvLight
            position: Qt.vector3d(-115, 98.5, 45)
            color: "#80c8ff"
            brightness: 120.0
            linearFade: 0.02
        }

        // Bedside Lamp Warm Amber-Yellow Light (#FFAA33)
        PointLight {
            id: deskLampLight
            position: Qt.vector3d(62, 65, -120)
            color: "#ffaa33"
            brightness: 80.0
            linearFade: 0.04
        }

        // PC Workstation / Laptop Phosphor Glow
        PointLight {
            id: monitorLight
            position: Qt.vector3d(85, 88, 15)
            color: "#40e0d0"
            brightness: 35.0
            linearFade: 0.03
        }

        // =====================================================================
        // 1. ARCHITECTURAL SHELL & UVS (models_obj: floor, left, center, right)
        // =====================================================================
        Node {
            id: architecturalShell

            // Floor (models_obj/room_floor.obj)
            Model {
                id: floorMesh
                source: root.assetsRoot + "models_obj/room_floor.obj"
                position: Qt.vector3d(0, 0, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/carpet_dither_tile.png"
                            scaleU: 8
                            scaleV: 8
                        }
                        roughness: 1.0
                        specularAmount: 0.0
                        metalness: 0.0
                    }
                ]
            }

            // Left Wall (models_obj/wall_left.obj) - Posters Atlas: Nu-Metal (Korn, Slipknot, Deftones)
            Model {
                id: wallLeftMesh
                source: root.assetsRoot + "models_obj/wall_left.obj"
                position: Qt.vector3d(-200, 120, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/atlases/posters_nu_metal_left.png"
                        }
                        roughness: 0.85
                        metalness: 0.05
                    }
                ]
            }

            // Center Wall (models_obj/wall_center.obj) - Posters Atlas: Center Band (Linkin Park, Deftones)
            Model {
                id: wallCenterMesh
                source: root.assetsRoot + "models_obj/wall_center.obj"
                position: Qt.vector3d(0, 120, -200)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/atlases/posters_center_band.png"
                        }
                        roughness: 0.85
                        metalness: 0.05
                    }
                ]
            }

            // Right Wall (models_obj/wall_right.obj) - Posters Atlas: PS1 Game Boxes
            Model {
                id: wallRightMesh
                source: root.assetsRoot + "models_obj/wall_right.obj"
                position: Qt.vector3d(200, 120, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/atlases/posters_ps1_games.png"
                        }
                        roughness: 0.85
                        metalness: 0.05
                    }
                ]
            }
        }

        // =====================================================================
        // 2. FURNITURE & LARGE PROPS
        // =====================================================================
        Node {
            id: furnitureSection

            // TV Dresser (models_obj/dresser_tv_stand.obj)
            Model {
                id: tvDresserMesh
                source: root.assetsRoot + "models_obj/dresser_tv_stand.obj"
                position: Qt.vector3d(-110, 36, 15)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.9
                        metalness: 0.0
                    }
                ]
            }

            // Bed Frame (models_obj/bed_frame.obj)
            Model {
                id: bedFrameMesh
                source: root.assetsRoot + "models_obj/bed_frame.obj"
                position: Qt.vector3d(-10, 18.5, -95)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.9
                        metalness: 0.0
                    }
                ]
            }

            // Bedding (models_obj/bed_mattress_duvet.obj)
            Model {
                id: bedDuvetMesh
                source: root.assetsRoot + "models_obj/bed_mattress_duvet.obj"
                position: Qt.vector3d(-10, 38.0, -95)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/duvet_charcoal_cloth_tile.png"
                        }
                        roughness: 1.0
                        metalness: 0.0
                    }
                ]
            }

            // Folded Band T-Shirt Graphic Skull Decal on Bed Duvet
            Model {
                source: "#Rectangle"
                scale: Qt.vector3d(0.48, 0.42, 1)
                position: Qt.vector3d(-10, 49.2, -75)
                eulerRotation: Qt.vector3d(-90, 8, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_band_shirt_skull.png"
                        }
                        roughness: 1.0
                    }
                ]
            }

            // Nightstand (models_obj/nightstand.obj)
            Model {
                id: nightstandMesh
                source: root.assetsRoot + "models_obj/nightstand.obj"
                position: Qt.vector3d(62, 26, -120)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.9
                        metalness: 0.0
                    }
                ]
            }

            // Bedside Brass Lamp (propped on nightstand top)
            Node {
                position: Qt.vector3d(62, 51, -120)

                Model {
                    source: "#Cylinder"
                    scale: Qt.vector3d(0.18, 0.28, 0.18)
                    position: Qt.vector3d(0, 14, 0)
                    materials: [
                        PrincipledMaterial {
                            baseColorMap: Texture {
                                source: root.assetsRoot + "textures/props/prop_nightstand_lamp.png"
                            }
                            emissiveFactor: Qt.vector3d(0.8, 0.6, 0.2)
                            roughness: 0.8
                        }
                    ]
                }
            }

            // Computer Desk (models_obj/computer_desk.obj)
            Model {
                id: computerDeskMesh
                source: root.assetsRoot + "models_obj/computer_desk.obj"
                position: Qt.vector3d(115, 38.5, 10)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.9
                        metalness: 0.0
                    }
                ]
            }
        }

        // =====================================================================
        // 3. ELECTRONICS & EMISSIVES (CRT TV, PS1 Console, Alarm Clock, CRT PC)
        // =====================================================================
        Node {
            id: electronicsSection

            // Heavy beige/black 1990s CRT Television Unit
            Node {
                id: crtTvNode
                position: Qt.vector3d(-115, 98.5, 15)
                eulerRotation: Qt.vector3d(0, 18, 0)

                // TV Chassis
                Model {
                    source: root.assetsRoot + "models_obj/crt_tv_unit.obj"
                    position: Qt.vector3d(0, 0, 0)
                    materials: [
                        PrincipledMaterial {
                            baseColorMap: Texture {
                                source: root.assetsRoot + "textures/props/prop_crt_tv_screen.png"
                            }
                            roughness: 0.6
                        }
                    ]
                }

                // CRT Static Screen (DefaultMaterial, NoLighting, Emissive)
                Model {
                    source: "#Rectangle"
                    scale: Qt.vector3d(0.48, 0.38, 1)
                    position: Qt.vector3d(0, 0, 25.4)
                    materials: [
                        DefaultMaterial {
                            lighting: DefaultMaterial.NoLighting
                            diffuseColor: "#80c8ff"
                            emissiveFactor: Qt.vector3d(0.5, 0.78, 1.0)
                            emissiveMap: Texture {
                                source: root.assetsRoot + "textures/props/crt_tv_static_emissive.png"
                            }
                        }
                    ]
                }
            }

            // Classic 32-bit Home Game Console (PS1 aesthetic)
            Model {
                id: ps1ConsoleMesh
                source: root.assetsRoot + "models_obj/ps1_console.obj"
                position: Qt.vector3d(-58, 74.5, 20)
                eulerRotation: Qt.vector3d(0, 12, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_ps1_console.png"
                        }
                        roughness: 0.7
                    }
                ]
            }

            // Digital Alarm Clock ("12:07" Red Glowing Digits on Nightstand Front)
            Model {
                id: alarmClockScreen
                source: "#Rectangle"
                scale: Qt.vector3d(0.18, 0.10, 1)
                position: Qt.vector3d(55, 42, -97)
                eulerRotation: Qt.vector3d(0, -15, 0)
                materials: [
                    DefaultMaterial {
                        lighting: DefaultMaterial.NoLighting
                        diffuseColor: "#ff2222"
                        emissiveFactor: Qt.vector3d(1.0, 0.13, 0.13)
                        emissiveMap: Texture {
                            source: root.assetsRoot + "textures/emissive/clock_led_1207_emissive.png"
                        }
                    }
                ]
            }

            // Bulky Beige 90s CRT PC Monitor
            Model {
                id: crtPcMonitorMesh
                source: root.assetsRoot + "models_obj/crt_pc_monitor.obj"
                position: Qt.vector3d(130, 97.0, -12)
                eulerRotation: Qt.vector3d(0, -25, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_retro_pc_monitor.png"
                        }
                        roughness: 0.6
                    }
                ]
            }
        }

        // =====================================================================
        // 4. INTERACTIVE RETRO LAPTOP WORKSTATION ("terminal" ANCHOR)
        // =====================================================================
        Node {
            id: retroLaptopWorkstation
            position: Qt.vector3d(85, 77.8, 5)
            eulerRotation: Qt.vector3d(0, -15, 0)

            // Laptop Base Unit (models_obj/retro_laptop_base.obj)
            Model {
                id: laptopBaseMesh
                source: root.assetsRoot + "models_obj/retro_laptop_base.obj"
                position: Qt.vector3d(0, 0, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_retro_laptop_base.png"
                        }
                        roughness: 0.7
                    }
                ]
            }

            // Laptop Clamshell Lid Hinge Pivot at Rear Edge of Base (Z = -14, Y = 1.75)
            Node {
                id: laptopHinge
                position: Qt.vector3d(0, 1.75, -14)
                eulerRotation: Qt.vector3d(root.laptopLidAngle, 0, 0)

                // Clamshell Lid Mesh
                Model {
                    id: laptopLidMesh
                    source: root.assetsRoot + "models_obj/retro_laptop_lid.obj"
                    position: Qt.vector3d(0, 1.25, 13)
                    eulerRotation: Qt.vector3d(90, 0, 0)
                    materials: [
                        PrincipledMaterial {
                            baseColorMap: Texture {
                                source: root.assetsRoot + "textures/props/prop_retro_laptop_lid.png"
                            }
                            roughness: 0.7
                        }
                    ]
                }

                // Inside Screen: Reveals the active tiled workspace screen when opened!
                Model {
                    id: laptopScreenMesh
                    source: "#Rectangle"
                    scale: Qt.vector3d(0.32, 0.22, 1)
                    position: Qt.vector3d(0, 1.35, 13)
                    eulerRotation: Qt.vector3d(90, 0, 0)
                    materials: [
                        DefaultMaterial {
                            lighting: DefaultMaterial.NoLighting
                            diffuseColor: "#40e0d0"
                            emissiveFactor: Qt.vector3d(0.25, 0.88, 0.82)
                        }
                    ]
                }
            }
        }

        // =====================================================================
        // 5. CLUTTER & FLOOR SCATTER (Skateboard, CDs, Cans, Guitar, Audio)
        // =====================================================================
        Node {
            id: clutterPropsSection

            // Complete Skateboard Lying Wheels-Down on Floor Beside Bed
            Model {
                id: skateboardMesh
                source: root.assetsRoot + "models_obj/skateboard.obj"
                position: Qt.vector3d(35, 6.5, 35)
                eulerRotation: Qt.vector3d(0, 20, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_skateboard.png"
                        }
                        roughness: 0.8
                    }
                ]
            }

            // CD Jewel Case on TV Dresser
            Model {
                id: cdCaseDresser
                source: root.assetsRoot + "models_obj/cd_jewel_case.obj"
                position: Qt.vector3d(-50, 71.5, 30)
                eulerRotation: Qt.vector3d(0, 25, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_cd_jewel_cases.png"
                        }
                        roughness: 0.4
                    }
                ]
            }

            // CD Jewel Case on Floor
            Model {
                id: cdCaseFloor
                source: root.assetsRoot + "models_obj/cd_jewel_case.obj"
                position: Qt.vector3d(-35, 1.5, 70)
                eulerRotation: Qt.vector3d(0, 35, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_cd_jewel_cases.png"
                        }
                        roughness: 0.4
                    }
                ]
            }

            // Exposed Compact Disc (High Specular & Metalness Reflection)
            Model {
                id: compactDiscMesh
                source: root.assetsRoot + "models_obj/cd_jewel_case.obj"
                position: Qt.vector3d(-48, 1.5, 85)
                eulerRotation: Qt.vector3d(0, -15, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_compact_disc.png"
                        }
                        roughness: 0.2
                        metalness: 0.8
                    }
                ]
            }

            // Soda Can on Desk
            Model {
                id: sodaCanDesk
                source: root.assetsRoot + "models_obj/soda_can.obj"
                position: Qt.vector3d(65, 82.0, 5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_soda_can_cola.png"
                        }
                        roughness: 0.35
                        metalness: 0.6
                    }
                ]
            }

            // Crushed Soda Can on Floor
            Model {
                id: sodaCanFloor
                source: root.assetsRoot + "models_obj/soda_can.obj"
                position: Qt.vector3d(48, 4.0, 80)
                eulerRotation: Qt.vector3d(80, 25, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_soda_can_cola.png"
                        }
                        roughness: 0.35
                        metalness: 0.6
                    }
                ]
            }

            // Discarded Band T-Shirt on Floor (Foreground)
            Model {
                source: "#Rectangle"
                scale: Qt.vector3d(0.55, 0.42, 1)
                position: Qt.vector3d(-18, 1.2, 110)
                eulerRotation: Qt.vector3d(-90, -5, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_band_shirt_skull.png"
                        }
                        roughness: 1.0
                    }
                ]
            }

            // Electric Guitar Propped Against Desk Frame
            Model {
                source: "#Cube"
                scale: Qt.vector3d(0.30, 0.90, 0.08)
                position: Qt.vector3d(155, 38, 25)
                eulerRotation: Qt.vector3d(-14, -30, 18)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_electric_guitar.png"
                        }
                        roughness: 0.4
                        metalness: 0.2
                    }
                ]
            }

            // Boombox / Stereo Audio Unit on Desk ("music" anchor framing)
            Model {
                source: "#Cube"
                scale: Qt.vector3d(0.55, 0.30, 0.22)
                position: Qt.vector3d(150, 84, -15)
                eulerRotation: Qt.vector3d(0, -25, 0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/atlases/desk_boombox_audio.png"
                        }
                        roughness: 0.5
                    }
                ]
            }
        }
    }
}
