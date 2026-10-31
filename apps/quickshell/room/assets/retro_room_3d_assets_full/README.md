# Nu-Metal 90s/2000s Teen Bedroom Asset Pack
Art Style: PS1 / Retro Indie Game ("Tache Noire" aesthetic)

## Folder Structure
- `textures/posters/`: High-detail cropped individual nu-metal, gig flyer, and PS1 game posters.
- `textures/atlases/`: Composite wall/prop atlases for easy multi-poster UV mapping.
- `textures/props/`: Hardware, CRT screen, skateboard, soda can, digital clock.
- `textures/environment/`: Tileable retro dark wood grain, dithered carpet, dark wall plaster, and CRT scanline overlay.
- `textures/ui/`: Retro inventory and health bar HUD elements.
- `materials/`: JSON material configuration specs ready for Qt Quick 3D `PrincipledMaterial` / `CustomMaterial`.

## Recommended Qt Quick 3D Configuration
- Texture filtering: Nearest or Bilinear (keep low-res retro feel, avoid heavy anisotropic filtering).
- Antialiasing: `SceneEnvironment.NoAA` for crisp retro edges.
- Post-process: Pass `environment.effects: [ VhsEffect { ... } ]` with scanline and dither uniforms.
