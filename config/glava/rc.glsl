/* Abyssal Biopunk / Infernal Retro glava entry point.
   Full option reference: the stock rc.glsl shipped with the glava package
   (nix store path under share/xdg/glava/rc.glsl) -- this file intentionally
   only carries the options we deviate from; everything else keeps glava's
   defaults. */

/* radial: a pulsing, rotating audio-reactive ring -- see config/glava/radial.glsl
   for the Abyssal Biopunk color/geometry overrides. */
#request mod radial

#request setfloating  false
#request setdecorated false
#request setfocused   false
#request setmaximized false

/* True compositor transparency; picom (config/picom/picom.conf) is already
   running with compositing enabled. */
#request setopacity "native"
#request setbg 00000000

/* Desktop window type/state is set by `-d`/`--desktop` at launch time (see
   config/autostart/glava.desktop), which auto-detects Xfwm4 -- no manual
   setxwintype/addxwinstate needed here. */

/* Let clicks fall through to desktop icons/windows underneath. */
#request setclickthrough true

#request settitle "Abyssal Biopunk Visualizer"
#request setgeometry 0 0 1920 1080

#request setversion 3 3
#request setshaderversion 330

#request setsource "auto"
#request setswap 1
#request setinterpolate true
#request setframerate 60
#request setprintframes false

#request setsamplesize 1024
#request setbufsize 4096
#request setsamplerate 22050
