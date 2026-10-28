// vendor_impl.cpp --- the single translation unit that actually compiles
// miniaudio/stb_perlin's function bodies.
//
// Both are single-header libraries: including them with their
// *_IMPLEMENTATION macro defined pulls in definitions, not just
// declarations. Under the old unity build that was safe because the whole
// plugin was one translation unit; now that engine/game sources are
// compiled separately, defining the macro in globals.hpp (included
// everywhere) would emit duplicate symbols in every object file. So
// globals.hpp includes these two files WITHOUT the macro (declarations
// only, safe to repeat per-TU), and this file is the one place that
// defines the macro and gets the real implementation.
#define MINIAUDIO_IMPLEMENTATION
#define STB_PERLIN_IMPLEMENTATION
#include "miniaudio.c"
#include "stb_perlin.c"
