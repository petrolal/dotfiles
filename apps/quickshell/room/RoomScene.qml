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
            position: Qt.vector3d(78, 64, 34)
            eulerRotation: Qt.vector3d(-8, 38, 0)
        }

        Node {
            id: anchorMusic
            position: Qt.vector3d(92, 52, 92)
            eulerRotation: Qt.vector3d(-6, 36, 0)
        }

        Component.onCompleted: {
            anchorRegistry.registerAnchor("home", anchorHome);
            anchorRegistry.registerAnchor("terminal", anchorTerminal);
            anchorRegistry.registerAnchor("music", anchorMusic);
        }

        // =====================================================================
        // LIGHTING (Chiaroscuro & Hard Cel-Shaded Contrast)
        // =====================================================================

        // Ambient & Directional Fill with Crisp Shadow Casting
        DirectionalLight {
            eulerRotation: Qt.vector3d(-42, 32, 0)
            color: "#ded8ee"
            ambientColor: "#141318"
            brightness: 1.15
            castsShadow: true
            shadowFactor: 70.0
            shadowBias: 0.05
            shadowMapQuality: Light.ShadowMapQualityHigh
        }

        // CRT Television Cold Bluish-White Light (#80C8FF) with CRT Phosphor Flicker
        PointLight {
            id: tvLight
            position: Qt.vector3d(-105, 46, 12)
            color: "#80c8ff"
            brightness: 115.0
            linearFade: 0.018
            castsShadow: true
            shadowFactor: 65.0
            shadowMapQuality: Light.ShadowMapQualityMedium

            SequentialAnimation on brightness {
                loops: Animation.Infinite
                running: true
                NumberAnimation { to: 124.0; duration: 90; easing.type: Easing.InOutQuad }
                NumberAnimation { to: 106.0; duration: 130; easing.type: Easing.InOutQuad }
                NumberAnimation { to: 128.0; duration: 70; easing.type: Easing.InOutQuad }
                NumberAnimation { to: 112.0; duration: 110; easing.type: Easing.InOutQuad }
            }
        }

        // Bedside Lamp Warm Amber-Yellow Light (#FFAA33)
        PointLight {
            id: deskLampLight
            position: Qt.vector3d(62, 54, -120)
            color: "#ffaa33"
            brightness: 88.0
            linearFade: 0.032
            castsShadow: true
            shadowFactor: 60.0
            shadowMapQuality: Light.ShadowMapQualityMedium
        }

        // PC Workstation / Laptop Phosphor Glow (#40E0D0)
        PointLight {
            id: monitorLight
            position: Qt.vector3d(112, 60, 10)
            color: "#40e0d0"
            brightness: 40.0
            linearFade: 0.025
        }

        // Digital Alarm Clock Red LED Bloom Light (#FF1A1A)
        PointLight {
            id: clockLight
            position: Qt.vector3d(54, 22, -104)
            color: "#ff1a1a"
            brightness: 18.0
            linearFade: 0.12
        }

        // =====================================================================
        // 1. ARCHITECTURAL SHELL & ENVIRONMENT
        // =====================================================================
        Node {
            id: architecturalShell

            // Floor (floorFull_mesh.mesh)
            Model {
                id: floorMesh
                source: root.assetsRoot + "models_mesh/meshes/floorFull_mesh.mesh"
                position: Qt.vector3d(0, 0, 0)
                scale: Qt.vector3d(40, 1, 40)
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
                        cullMode: Material.NoCulling
                    }
                ]
            }

            // Left Wall (wall_mesh.mesh) - Posters Atlas: Nu-Metal (Korn, Slipknot)
            Model {
                id: wallLeftMesh
                source: root.assetsRoot + "models_mesh/meshes/wall_mesh.mesh"
                position: Qt.vector3d(-200, 120, 0)
                eulerRotation: Qt.vector3d(0, 90, 0)
                scale: Qt.vector3d(40, 18.6, 1)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/atlases/posters_nu_metal_left.png"
                        }
                        roughness: 0.85
                        metalness: 0.05
                        cullMode: Material.NoCulling
                    }
                ]
            }

            // Center Wall (wall_mesh.mesh) - Posters Atlas: Center Band (Linkin Park, Deftones)
            Model {
                id: wallCenterMesh
                source: root.assetsRoot + "models_mesh/meshes/wall_mesh.mesh"
                position: Qt.vector3d(0, 120, -200)
                eulerRotation: Qt.vector3d(0, 0, 0)
                scale: Qt.vector3d(40, 18.6, 1)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/atlases/posters_center_band.png"
                        }
                        roughness: 0.85
                        metalness: 0.05
                        cullMode: Material.NoCulling
                    }
                ]
            }

            // Right Wall (wall_mesh.mesh) - Posters Atlas: PS1 Game Boxes & Mecha
            Model {
                id: wallRightMesh
                source: root.assetsRoot + "models_mesh/meshes/wall_mesh.mesh"
                position: Qt.vector3d(200, 120, 0)
                eulerRotation: Qt.vector3d(0, -90, 0)
                scale: Qt.vector3d(40, 18.6, 1)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/atlases/posters_ps1_games.png"
                        }
                        roughness: 0.85
                        metalness: 0.05
                        cullMode: Material.NoCulling
                    }
                ]
            }

            // Floor Area Rug (rugRectangle_mesh.mesh)
            Model {
                id: rugMesh
                source: root.assetsRoot + "models_mesh/meshes/rugRectangle_mesh.mesh"
                position: Qt.vector3d(5, 0.4, 25)
                eulerRotation: Qt.vector3d(0, 5, 0)
                scale: Qt.vector3d(12.5, 1, 14.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/rug_rectangle.png"
                        }
                        roughness: 0.95
                        metalness: 0.0
                        cullMode: Material.NoCulling
                    }
                ]
            }
        }

        // =====================================================================
        // 2. BED & SLEEP AREA
        // =====================================================================
        Node {
            id: bedArea

            // Bed Single (bedSingle_mesh.mesh)
            Model {
                id: bedSingleMesh
                source: root.assetsRoot + "models_mesh/meshes/bedSingle_mesh.mesh"
                position: Qt.vector3d(-25, 0, -95)
                eulerRotation: Qt.vector3d(0, 90, 0)
                scale: Qt.vector3d(9.5, 9.5, 9.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/duvet_charcoal_cloth_tile.png"
                        }
                        roughness: 0.95
                        metalness: 0.0
                    },
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.85
                        metalness: 0.0
                    },
                    PrincipledMaterial {
                        baseColor: "#6a6e74"
                        roughness: 0.4
                        metalness: 0.6
                    }
                ]
            }

            // Long Head Pillow (pillowLong_mesh.mesh)
            Model {
                id: pillowLongMesh
                source: root.assetsRoot + "models_mesh/meshes/pillowLong_mesh.mesh"
                position: Qt.vector3d(-25, 29.5, -138)
                eulerRotation: Qt.vector3d(15, 90, 0)
                scale: Qt.vector3d(9.0, 9.0, 9.0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/pillow_fabric_tile.png"
                        }
                        roughness: 0.9
                    }
                ]
            }

            // Band T-Shirt Spread Across Bed (t_Shirt_male_mesh.mesh)
            Model {
                id: bedShirtMesh
                source: root.assetsRoot + "models_mesh/meshes/t_Shirt_male_mesh.mesh"
                position: Qt.vector3d(-22, 28.5, -85)
                eulerRotation: Qt.vector3d(0, 25, 0)
                scale: Qt.vector3d(36, 36, 36)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_band_shirt_skull.png"
                        }
                        roughness: 0.95
                    }
                ]
            }

            // Bedside Nightstand (cabinetBedDrawerTable_mesh.mesh)
            Model {
                id: nightstandMesh
                source: root.assetsRoot + "models_mesh/meshes/cabinetBedDrawerTable_mesh.mesh"
                position: Qt.vector3d(62, 0, -120)
                eulerRotation: Qt.vector3d(0, -90, 0)
                scale: Qt.vector3d(11.0, 11.0, 11.0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.85
                    }
                ]
            }

            // Bedside Round Table Lamp (lampRoundTable_mesh.mesh)
            Model {
                id: lampMesh
                source: root.assetsRoot + "models_mesh/meshes/lampRoundTable_mesh.mesh"
                position: Qt.vector3d(62, 29.0, -120)
                scale: Qt.vector3d(9.0, 9.0, 9.0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_nightstand_lamp.png"
                        }
                        emissiveFactor: Qt.vector3d(0.85, 0.65, 0.25)
                        roughness: 0.5
                        metalness: 0.3
                    }
                ]
            }

            // Digital Alarm Clock ("12:07" Red Glowing Digits)
            Model {
                id: alarmClockScreen
                source: "#Rectangle"
                scale: Qt.vector3d(0.18, 0.10, 1)
                position: Qt.vector3d(54, 21.5, -107)
                eulerRotation: Qt.vector3d(0, -15, 0)
                materials: [
                    DefaultMaterial {
                        lighting: DefaultMaterial.NoLighting
                        diffuseColor: "#ff1a1a"
                        emissiveFactor: Qt.vector3d(1.0, 0.1, 0.1)
                        emissiveMap: Texture {
                            source: root.assetsRoot + "textures/emissive/clock_led_1207_emissive.png"
                        }
                    }
                ]
            }
        }

        // =====================================================================
        // 3. TV DRESSER & PLAYSTATION 1 SETUP
        // =====================================================================
        Node {
            id: tvConsoleSection

            // TV Dresser (cabinetTelevision_mesh.mesh)
            Model {
                id: dresserMesh
                source: root.assetsRoot + "models_mesh/meshes/cabinetTelevision_mesh.mesh"
                position: Qt.vector3d(-122, 0, 20)
                eulerRotation: Qt.vector3d(0, 90, 0)
                scale: Qt.vector3d(10.5, 10.5, 10.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.85
                    }
                ]
            }

            // Dresser Doors (cabinetTelevisionDoors_mesh.mesh)
            Model {
                id: dresserDoorsMesh
                source: root.assetsRoot + "models_mesh/meshes/cabinetTelevisionDoors_mesh.mesh"
                position: Qt.vector3d(-122, 0, 20)
                eulerRotation: Qt.vector3d(0, 90, 0)
                scale: Qt.vector3d(10.5, 10.5, 10.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.85
                    }
                ]
            }

            // CRT TV Chassis Unit (televisionVintage_mesh.mesh)
            Model {
                id: crtTvMesh
                source: root.assetsRoot + "models_mesh/meshes/televisionVintage_mesh.mesh"
                position: Qt.vector3d(-118, 32.5, 2)
                eulerRotation: Qt.vector3d(0, 110, 0)
                scale: Qt.vector3d(9.5, 9.5, 9.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_crt_tv_unit.png"
                        }
                        roughness: 0.65
                    }
                ]
            }

            // CRT Television Antenna (televisionAntenna_mesh.mesh)
            Model {
                id: antennaMesh
                source: root.assetsRoot + "models_mesh/meshes/televisionAntenna_mesh.mesh"
                position: Qt.vector3d(-118, 58.2, 2)
                eulerRotation: Qt.vector3d(0, 110, 0)
                scale: Qt.vector3d(8.5, 8.5, 8.5)
                materials: [
                    PrincipledMaterial {
                        baseColor: "#989ca0"
                        roughness: 0.25
                        metalness: 0.85
                    }
                ]
            }

            // CRT Static Glowing Screen (DefaultMaterial, NoLighting)
            Model {
                id: crtTvScreenMesh
                source: "#Rectangle"
                scale: Qt.vector3d(0.38, 0.28, 1)
                position: Qt.vector3d(-105, 45.0, 6.5)
                eulerRotation: Qt.vector3d(0, 110, 0)
                materials: [
                    DefaultMaterial {
                        lighting: DefaultMaterial.NoLighting
                        diffuseColor: "#80c8ff"
                        emissiveFactor: Qt.vector3d(0.6, 0.85, 1.0)
                        emissiveMap: Texture {
                            source: root.assetsRoot + "textures/props/crt_tv_static_emissive.png"
                        }
                    }
                ]
            }

            // PlayStation 1 Console (ps1_console_mesh.mesh)
            Model {
                id: ps1ConsoleMesh
                source: root.assetsRoot + "models_mesh/meshes/ps1_console_mesh.mesh"
                position: Qt.vector3d(-112, 32.8, 38)
                eulerRotation: Qt.vector3d(0, 85, 0)
                scale: Qt.vector3d(6.8, 6.8, 6.8)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_ps1_console.png"
                        }
                        roughness: 0.65
                        metalness: 0.05
                    }
                ]
            }

            // PlayStation 1 Controller (ps1_controller_mesh.mesh)
            Model {
                id: ps1ControllerMesh
                source: root.assetsRoot + "models_mesh/meshes/ps1_controller_mesh.mesh"
                position: Qt.vector3d(-96, 33.0, 48)
                eulerRotation: Qt.vector3d(0, 40, 0)
                scale: Qt.vector3d(6.5, 6.5, 6.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_ps1_controller.png"
                        }
                        roughness: 0.7
                        metalness: 0.05
                    }
                ]
            }

            // PS1 Game Case on Dresser (pal_case_front_mesh.mesh)
            Model {
                id: ps1GameCaseDresser
                source: root.assetsRoot + "models_mesh/meshes/pal_case_front_mesh.mesh"
                position: Qt.vector3d(-115, 33.0, 56)
                eulerRotation: Qt.vector3d(0, 75, 0)
                scale: Qt.vector3d(2.2, 2.2, 2.2)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_cd_jewel_cases.png"
                        }
                        roughness: 0.35
                        metalness: 0.1
                    }
                ]
            }
        }

        // =====================================================================
        // 4. COMPUTER WORKSTATION & RETRO LAPTOP ("terminal" ANCHOR)
        // =====================================================================
        Node {
            id: computerWorkstation

            // Computer Desk (desk_mesh.mesh)
            Model {
                id: deskMesh
                source: root.assetsRoot + "models_mesh/meshes/desk_mesh.mesh"
                position: Qt.vector3d(120, 0, 10)
                eulerRotation: Qt.vector3d(0, -90, 0)
                scale: Qt.vector3d(11.5, 11.5, 11.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/retro_dark_wood_tile.png"
                        }
                        roughness: 0.85
                    }
                ]
            }

            // Swivel Desk Chair (chairDesk_mesh.mesh)
            Model {
                id: chairMesh
                source: root.assetsRoot + "models_mesh/meshes/chairDesk_mesh.mesh"
                position: Qt.vector3d(76, 0, 12)
                eulerRotation: Qt.vector3d(0, 80, 0)
                scale: Qt.vector3d(7.5, 7.5, 7.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/environment/duvet_charcoal_cloth_tile.png"
                        }
                        roughness: 0.95
                    }
                ]
            }

            // Bulky Beige 90s CRT PC Monitor (computerScreen_mesh.mesh)
            Model {
                id: pcMonitorMesh
                source: root.assetsRoot + "models_mesh/meshes/computerScreen_mesh.mesh"
                position: Qt.vector3d(130, 44.5, -15)
                eulerRotation: Qt.vector3d(0, -115, 0)
                scale: Qt.vector3d(8.5, 8.5, 8.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_retro_pc_monitor.png"
                        }
                        roughness: 0.75
                    }
                ]
            }

            // Active CRT Monitor Screen Display (Winamp / Windows 98)
            Model {
                id: pcMonitorScreenMesh
                source: "#Rectangle"
                scale: Qt.vector3d(0.24, 0.18, 1)
                position: Qt.vector3d(125, 57.0, -13)
                eulerRotation: Qt.vector3d(0, -115, 0)
                materials: [
                    DefaultMaterial {
                        lighting: DefaultMaterial.NoLighting
                        diffuseColor: "#00ff88"
                        emissiveFactor: Qt.vector3d(0.4, 0.95, 0.6)
                        emissiveMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_pc_screen_display.png"
                        }
                    }
                ]
            }

            // PC Desktop Horizontal Tower (pc_tower_unit_mesh.mesh)
            Model {
                id: pcTowerMesh
                source: root.assetsRoot + "models_mesh/meshes/pc_tower_unit_mesh.mesh"
                position: Qt.vector3d(142, 0, -25)
                eulerRotation: Qt.vector3d(0, -90, 0)
                scale: Qt.vector3d(8.5, 8.5, 8.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_pc_tower.png"
                        }
                        roughness: 0.75
                        metalness: 0.1
                    }
                ]
            }

            // Beige PC Keyboard (computerKeyboard_mesh.mesh)
            Model {
                id: keyboardMesh
                source: root.assetsRoot + "models_mesh/meshes/computerKeyboard_mesh.mesh"
                position: Qt.vector3d(116, 44.8, -10)
                eulerRotation: Qt.vector3d(0, -115, 0)
                scale: Qt.vector3d(7.5, 7.5, 7.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_retro_pc_monitor.png"
                        }
                        roughness: 0.8
                    }
                ]
            }

            // Beige PC Mouse (computerMouse_mesh.mesh)
            Model {
                id: mouseMesh
                source: root.assetsRoot + "models_mesh/meshes/computerMouse_mesh.mesh"
                position: Qt.vector3d(112, 44.8, -25)
                eulerRotation: Qt.vector3d(0, -115, 0)
                scale: Qt.vector3d(7.5, 7.5, 7.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_retro_pc_monitor.png"
                        }
                        roughness: 0.8
                    }
                ]
            }

            // Interactive Retro Laptop Workstation (laptop_mesh.mesh)
            Node {
                id: laptopStation
                position: Qt.vector3d(96, 44.5, 20)
                eulerRotation: Qt.vector3d(0, -105, 0)

                // Laptop Base
                Model {
                    id: laptopBaseMesh
                    source: root.assetsRoot + "models_mesh/meshes/laptop_mesh.mesh"
                    position: Qt.vector3d(0, 0, 0)
                    scale: Qt.vector3d(7.5, 7.5, 7.5)
                    materials: [
                        PrincipledMaterial {
                            baseColorMap: Texture {
                                source: root.assetsRoot + "textures/props/prop_retro_laptop_base.png"
                            }
                            roughness: 0.7
                            metalness: 0.1
                        }
                    ]
                }

                // Clamshell Opening Screen Lid
                Node {
                    id: laptopHinge
                    position: Qt.vector3d(0, 3.5, -8)
                    eulerRotation: Qt.vector3d(root.laptopLidAngle, 0, 0)

                    Model {
                        source: "#Rectangle"
                        scale: Qt.vector3d(0.20, 0.14, 1)
                        position: Qt.vector3d(0, 7.0, 0)
                        eulerRotation: Qt.vector3d(90, 0, 0)
                        materials: [
                            DefaultMaterial {
                                lighting: DefaultMaterial.NoLighting
                                diffuseColor: "#40e0d0"
                                emissiveFactor: Qt.vector3d(0.3, 0.9, 0.85)
                                emissiveMap: Texture {
                                    source: root.assetsRoot + "textures/props/prop_pc_screen_display.png"
                                }
                            }
                        ]
                    }
                }
            }
        }

        // =====================================================================
        // 5. AUDIO CORNER & ELECTRIC GUITAR ("music" ANCHOR)
        // =====================================================================
        Node {
            id: audioCorner

            // Electric Guitar Component (body / neck / headstock)
            Node {
                id: guitarComponent
                position: Qt.vector3d(142, 0, 42)
                eulerRotation: Qt.vector3d(-12, -25, 14)
                scale: Qt.vector3d(82.0, 82.0, 82.0)
            }

            // 90s Dual-Cassette Boombox Stereo (radio_mesh.mesh)
            Model {
                id: boomboxMesh
                source: root.assetsRoot + "models_mesh/meshes/radio_mesh.mesh"
                position: Qt.vector3d(135, 44.5, 32)
                eulerRotation: Qt.vector3d(0, -110, 0)
                scale: Qt.vector3d(5.5, 5.5, 5.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_boombox_radio.png"
                        }
                        roughness: 0.6
                        metalness: 0.3
                    }
                ]
            }

            // Studio Over-Ear Headphones Component
            Node {
                id: headphonesComponent
                position: Qt.vector3d(122, 44.8, 38)
                eulerRotation: Qt.vector3d(80, 15, -45)
                scale: Qt.vector3d(4.5, 4.5, 4.5)
            }

            // Bookshelf Speaker (speaker_mesh.mesh)
            Model {
                id: speakerMesh
                source: root.assetsRoot + "models_mesh/meshes/speaker_mesh.mesh"
                position: Qt.vector3d(145, 0, -45)
                eulerRotation: Qt.vector3d(0, -75, 0)
                scale: Qt.vector3d(9.5, 9.5, 9.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_speaker.png"
                        }
                        roughness: 0.8
                        metalness: 0.05
                    }
                ]
            }

            // Portable CD Discman (portable_discman_mesh.mesh)
            Model {
                id: discmanMesh
                source: root.assetsRoot + "models_mesh/meshes/portable_discman_mesh.mesh"
                position: Qt.vector3d(105, 44.5, 36)
                eulerRotation: Qt.vector3d(0, -30, 0)
                scale: Qt.vector3d(5.5, 5.5, 5.5)
                materials: [
                    PrincipledMaterial {
                        baseColor: "#808a96"
                        roughness: 0.3
                        metalness: 0.7
                    }
                ]
            }

            // Exposed Compact Disc on Desk (compact_disc_mesh.mesh)
            Model {
                id: deskDiscMesh
                source: root.assetsRoot + "models_mesh/meshes/compact_disc_mesh.mesh"
                position: Qt.vector3d(112, 44.6, 30)
                eulerRotation: Qt.vector3d(0, -10, 0)
                scale: Qt.vector3d(5.5, 5.5, 5.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_compact_disc.png"
                        }
                        roughness: 0.15
                        metalness: 0.85
                    }
                ]
            }
        }

        // =====================================================================
        // 6. CLUTTER & FLOOR SCATTER PROPS
        // =====================================================================
        Node {
            id: clutterPropsSection

            // Skateboard Component Lying Wheels-Down on Floor
            Node {
                id: skateboardComponent
                position: Qt.vector3d(25, 0, 50)
                eulerRotation: Qt.vector3d(0, 25, 0)
                scale: Qt.vector3d(28.0, 28.0, 28.0)
            }

            // Discarded Band T-Shirt on Floor (t_Shirt_male_mesh.mesh)
            Model {
                id: floorShirtMesh
                source: root.assetsRoot + "models_mesh/meshes/t_Shirt_male_mesh.mesh"
                position: Qt.vector3d(-15, 0.5, 95)
                eulerRotation: Qt.vector3d(0, 18, 0)
                scale: Qt.vector3d(42.0, 42.0, 42.0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_band_shirt_skull.png"
                        }
                        roughness: 0.95
                    }
                ]
            }

            // Upright Classic Soda Can on Desk (soda_can_mesh.mesh)
            Model {
                id: sodaCanDesk
                source: root.assetsRoot + "models_mesh/meshes/soda_can_mesh.mesh"
                position: Qt.vector3d(108, 44.5, 5)
                scale: Qt.vector3d(32.0, 32.0, 32.0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_soda_can_cola.png"
                        }
                        roughness: 0.35
                        metalness: 0.65
                    }
                ]
            }

            // Crushed Soda Can on Floor (soda_can_crushed_mesh.mesh)
            Model {
                id: sodaCanFloor
                source: root.assetsRoot + "models_mesh/meshes/soda_can_crushed_mesh.mesh"
                position: Qt.vector3d(45, 0.5, 75)
                eulerRotation: Qt.vector3d(75, 40, -15)
                scale: Qt.vector3d(32.0, 32.0, 32.0)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_soda_can_cola.png"
                        }
                        roughness: 0.35
                        metalness: 0.65
                    }
                ]
            }

            // PS1 Game CD Jewel Case on Floor (pal_case_front_mesh.mesh)
            Model {
                id: cdCaseFloor
                source: root.assetsRoot + "models_mesh/meshes/pal_case_front_mesh.mesh"
                position: Qt.vector3d(-35, 0.5, 68)
                eulerRotation: Qt.vector3d(0, 35, 0)
                scale: Qt.vector3d(2.2, 2.2, 2.2)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_cd_jewel_cases.png"
                        }
                        roughness: 0.35
                        metalness: 0.1
                    }
                ]
            }

            // Loose Compact Disc on Floor (compact_disc_mesh.mesh)
            Model {
                id: cdLooseFloor
                source: root.assetsRoot + "models_mesh/meshes/compact_disc_mesh.mesh"
                position: Qt.vector3d(-48, 0.5, 82)
                eulerRotation: Qt.vector3d(0, -15, 0)
                scale: Qt.vector3d(5.5, 5.5, 5.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_compact_disc.png"
                        }
                        roughness: 0.15
                        metalness: 0.85
                    }
                ]
            }

            // Cardboard Box Closed on Floor (cardboardBoxClosed_mesh.mesh)
            Model {
                id: boxClosedMesh
                source: root.assetsRoot + "models_mesh/meshes/cardboardBoxClosed_mesh.mesh"
                position: Qt.vector3d(140, 0, 75)
                eulerRotation: Qt.vector3d(0, 18, 0)
                scale: Qt.vector3d(9.5, 9.5, 9.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_cardboard_box.png"
                        }
                        roughness: 0.95
                    }
                ]
            }

            // Wire Mesh Wastebasket Trashcan (trashcan_mesh.mesh)
            Model {
                id: trashcanMesh
                source: root.assetsRoot + "models_mesh/meshes/trashcan_mesh.mesh"
                position: Qt.vector3d(142, 0, -2)
                scale: Qt.vector3d(7.5, 7.5, 7.5)
                materials: [
                    PrincipledMaterial {
                        baseColorMap: Texture {
                            source: root.assetsRoot + "textures/props/prop_trashcan.png"
                        }
                        roughness: 0.7
                        metalness: 0.4
                    }
                ]
            }
        }
    }
}
