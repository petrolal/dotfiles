/* Abyssal Biopunk / Infernal Retro palette override for the "radial" module
   (same colors as config/xfce4/terminal/terminalrc and config/picom/picom.conf).
   Picked up via radial/1.frag's `#include "@radial.glsl"` because this file
   lives in the active glava config root (~/.config/glava). */

#define C_RADIUS 140
#define C_LINE 2
#define OUTLINE #16171d
#define NBARS 180
#define BAR_WIDTH 3.5
#define BAR_OUTLINE OUTLINE
#define BAR_OUTLINE_WIDTH 1
#define AMPLIFY 340
/* Bars glow from dark red core to ember amber at peak amplitude (d). */
#define COLOR (mix(vec4(0.62, 0.16, 0.17, 1.0), vec4(0.74, 0.48, 0.18, 1.0), clamp(d / 60.0, 0.0, 1.0)))
#define ROTATE (PI / 2)
#define INVERT 0
#define BAR_ALIAS_FACTOR 1.2
#define C_ALIAS_FACTOR 1.8
#define CENTER_OFFSET_Y 0
#define CENTER_OFFSET_X 0

#request setgravitystep 4.5
#request setsmoothfactor 0.025
