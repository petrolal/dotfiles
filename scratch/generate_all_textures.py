import os
import subprocess

BASE_DIR = "/home/petrolal/dotfiles/apps/quickshell/room/assets"
TEX_DIR = os.path.join(BASE_DIR, "textures")
OBJ_TEX_DIR = os.path.join(BASE_DIR, "models_obj", "Textures")

os.makedirs(os.path.join(TEX_DIR, "environment"), exist_ok=True)
os.makedirs(os.path.join(TEX_DIR, "posters"), exist_ok=True)
os.makedirs(os.path.join(TEX_DIR, "atlases"), exist_ok=True)
os.makedirs(os.path.join(TEX_DIR, "props"), exist_ok=True)
os.makedirs(os.path.join(TEX_DIR, "emissive"), exist_ok=True)
os.makedirs(OBJ_TEX_DIR, exist_ok=True)

def run(cmd):
    subprocess.run(cmd, shell=True, check=True)

print("=== Generating Environment Textures ===")

# 1. Dark Grunge Plaster Wall Tile (512x512)
wall_path = os.path.join(TEX_DIR, "environment", "wall_dark_grunge_tile.png")
run(f"""
magick -size 512x512 xc:"#1c1a18" \\
  \\( -size 512x512 plasma:fractal -colorspace Gray -level 30%,70% -modulate 40,0,100 \\) \\
  -compose Multiply -composite \\
  \\( -size 512x512 xc:none -draw "fill none stroke '#100f0e' stroke-width 3 line 0,512 512,512 line 0,0 0,512" \\) \\
  -composite "{wall_path}"
""")

# 2. Dithered Bedroom Carpet Tile (256x256)
carpet_path = os.path.join(TEX_DIR, "environment", "carpet_dither_tile.png")
run(f"""
magick -size 256x256 xc:"#252932" \\
  \\( -size 256x256 xc:white +noise Random -virtual-pixel tile -blur 0x1 -level 30%,70% \\) \\
  -compose Overlay -composite \\
  -ordered-dither o8x8,4 \\
  -modulate 75,60,100 \\
  "{carpet_path}"
""")

# 3. Retro Dark Wood Tile (512x512)
wood_path = os.path.join(TEX_DIR, "environment", "retro_dark_wood_tile.png")
run(f"""
magick -size 64x512 plasma:fractal -colorspace Gray -scale 512x512! \\
  -level 20%,80% \\
  \\( -size 512x512 xc:"#362216" \\) +swap -compose Overlay -composite \\
  \\( -size 512x512 gradient:"#28170c-#462d1e" -evaluate Cosine 8 \\) \\
  -compose SoftLight -composite \\
  "{wood_path}"
""")

# 4. Duvet Charcoal Cloth Tile (512x512)
duvet_path = os.path.join(TEX_DIR, "environment", "duvet_charcoal_cloth_tile.png")
run(f"""
magick -size 512x512 xc:"#222429" \\
  \\( -size 512x512 xc:white +noise Random -blur 0x0.8 -level 40%,60% \\) \\
  -compose Overlay -composite \\
  \\( -size 512x512 gradient:"#181a1e-#2b2e35" -evaluate Cosine 3 \\) \\
  -compose SoftLight -composite \\
  "{duvet_path}"
""")

# 5. Pillow Fabric Tile (256x256)
pillow_path = os.path.join(TEX_DIR, "environment", "pillow_fabric_tile.png")
run(f"""
magick -size 256x256 xc:"#e6e2da" \\
  \\( -size 256x256 xc:white +noise Gaussian -blur 0x0.5 -level 45%,55% \\) \\
  -compose SoftLight -composite \\
  "{pillow_path}"
""")

# 6. Rectangular Nu-Metal Rug (512x512)
rug_path = os.path.join(TEX_DIR, "environment", "rug_rectangle.png")
run(f"""
magick -size 512x512 xc:"#1a191d" \\
  -fill "#28171a" -stroke "#7a1d26" -strokewidth 12 -draw "rectangle 24,24 488,488" \\
  -fill none -stroke "#b8860b" -strokewidth 4 -draw "rectangle 48,48 464,464" \\
  -fill none -stroke "#3d1318" -strokewidth 8 -draw "rectangle 70,70 442,442" \\
  -fill "#2a1e22" -stroke "#8b202a" -strokewidth 6 -draw "circle 256,256 256,160" \\
  -fill "#b8860b" -draw "circle 256,256 256,236" \\
  \\( -size 512x512 xc:white +noise Random -blur 0x1 -level 35%,65% \\) \\
  -compose Overlay -composite \\
  "{rug_path}"
""")

# 7. CRT Scanlines Overlay (128x128)
scan_path = os.path.join(TEX_DIR, "environment", "crt_scanlines_overlay.png")
run(f"""
magick -size 128x2 xc:black -size 128x2 xc:white -append -duplicate 31 -append "{scan_path}"
""")

print("=== Generating Posters ===")

# Deftones White Pony
deftones_white = os.path.join(TEX_DIR, "posters", "poster_deftones_white.png")
run(f"""
magick -size 512x768 xc:"#2c2e33" \\
  \\( -size 512x768 xc:white +noise Gaussian -blur 0x0.5 -level 40%,60% \\) -compose Overlay -composite \\
  -fill "#e2e6ea" -stroke "#d0d5dc" -strokewidth 2 \\
  -draw "path 'M 256,260 Q 230,220 200,240 Q 180,260 210,300 Q 220,330 200,380 Q 240,360 280,390 Q 320,350 290,300 Q 320,250 280,230 Z'" \\
  -font DejaVu-Sans-ExtraLight -pointsize 44 -fill "#d8dce2" -gravity south -annotate +0+120 "D E F T O N E S" \\
  -font DejaVu-Sans -pointsize 20 -fill "#8c94a0" -gravity south -annotate +0+85 "WHITE PONY" \\
  "{deftones_white}"
""")

# Deftones Live Band
deftones_band = os.path.join(TEX_DIR, "posters", "poster_deftones_band.png")
run(f"""
magick -size 512x768 xc:"#121016" \\
  \\( -size 512x450 gradient:"#3a1a45-#0d0b12" -evaluate Sin 4 \\) -geometry +0+80 -composite \\
  -fill "#20d0d8" -draw "circle 180,220 180,140" \\
  -fill "#d81860" -draw "circle 340,240 340,150" \\
  -fill "#000000" -draw "path 'M 160,420 Q 220,280 256,290 Q 300,280 360,420 Z'" \\
  -font DejaVu-Sans-Bold -pointsize 48 -fill "#f2eff8" -gravity north -annotate +0+35 "DEFTONES" \\
  -font DejaVu-Sans-Mono -pointsize 22 -fill "#30e0e8" -gravity south -annotate +0+50 "AROUND THE FUR TOUR" \\
  -blur 0x0.8 "{deftones_band}"
""")

# Korn Issues
korn_issues = os.path.join(TEX_DIR, "posters", "poster_korn_issue_left.png")
run(f"""
magick -size 512x768 xc:"#d48834" \\
  \\( -size 512x768 plasma:fractal -colorspace Gray -modulate 60,0,100 \\) -compose Multiply -composite \\
  -stroke "#26150a" -strokewidth 8 -fill "#c2b09a" \\
  -draw "circle 256,320 256,210" \\
  -draw "path 'M 210,410 L 170,560 L 230,560 L 256,480 L 282,560 L 342,560 L 302,410 Z'" \\
  -stroke "#100804" -strokewidth 4 -fill "#100804" \\
  -draw "line 210,290 235,315 line 235,290 210,315" \\
  -draw "line 275,290 300,315 line 300,290 275,315" \\
  -stroke "#100804" -strokewidth 5 -fill none \\
  -draw "path 'M 220,370 Q 256,395 292,370'" \\
  -font DejaVu-Serif-Bold -pointsize 64 -fill "#1c0d06" -stroke "#1c0d06" -strokewidth 2 -gravity north -annotate +0+40 "KoЯn" \\
  -font DejaVu-Sans-Bold -pointsize 32 -fill "#3a1e0f" -gravity south -annotate +0+60 "I S S U E S" \\
  "{korn_issues}"
""")

# Korn Distressed Logo
korn_logo = os.path.join(TEX_DIR, "posters", "poster_korn_logo.png")
run(f"""
magick -size 512x512 xc:"#0f0b08" \\
  \\( -size 512x512 plasma:fractal -colorspace Gray -modulate 40,0,100 \\) -compose Multiply -composite \\
  -font DejaVu-Serif-Bold -pointsize 110 -fill "#c8201a" -stroke "#4a0604" -strokewidth 4 \\
  -gravity center -annotate +0-20 "KoЯn" \\
  -font DejaVu-Sans-Bold -pointsize 26 -fill "#8c1612" -gravity center -annotate +0+70 "FOLLOW THE LEADER" \\
  "{korn_logo}"
""")

# Slipknot Band Photo
slipknot_photo = os.path.join(TEX_DIR, "posters", "poster_slipknot_photo.png")
run(f"""
magick -size 512x768 xc:"#150808" \\
  \\( -size 512x500 gradient:"#7a1010-#100404" -evaluate Sin 6 \\) -geometry +0+80 -composite \\
  -fill "#b51c1c" -draw "rectangle 60,320 130,550 rectangle 150,300 220,550 rectangle 240,280 310,550 rectangle 330,300 400,550 rectangle 420,330 480,550" \\
  -fill "#e0d8cc" -stroke "#000" -strokewidth 2 \\
  -draw "circle 95,300 95,265 circle 185,280 185,245 circle 275,260 275,225 circle 365,280 365,245 circle 450,305 450,270" \\
  -font DejaVu-Sans-Bold -pointsize 54 -fill "#d82424" -stroke "#200" -strokewidth 2 -gravity north -annotate +0+35 "SLIPKNOT" \\
  -font DejaVu-Sans-Mono -pointsize 20 -fill "#e0e0e0" -gravity south -annotate +0+60 "742617000027 - IOWA" \\
  "{slipknot_photo}"
""")

# Slipknot Nonagram / Tribal
slipknot_top = os.path.join(TEX_DIR, "posters", "poster_slipknot_top_left.png")
run(f"""
magick -size 512x512 xc:"#120303" \\
  \\( -size 512x512 xc:white +noise Random -blur 0x1 -level 30%,70% \\) -compose Overlay -composite \\
  -stroke "#b81414" -strokewidth 8 -fill none \\
  -draw "circle 256,256 256,60" \\
  -draw "polygon 256,70 330,390 140,210 372,210 182,390" \\
  -font DejaVu-Sans-Bold -pointsize 38 -fill "#e02020" -gravity south -annotate +0+35 "SLIPKNOT" \\
  "{slipknot_top}"
""")

# Linkin Park Hybrid Theory
linkin_park = os.path.join(TEX_DIR, "posters", "poster_linkin_park_hybrid.png")
run(f"""
magick -size 512x768 xc:"#bcaf96" \\
  \\( -size 512x768 plasma:fractal -colorspace Gray -modulate 50,0,100 \\) -compose Multiply -composite \\
  -stroke "#2c261e" -strokewidth 6 -fill "#7a2222" \\
  -draw "path 'M 256,220 L 285,340 L 265,520 L 245,520 L 230,340 Z'" \\
  -stroke "#8a2424" -strokewidth 4 -fill "#a43232" \\
  -draw "path 'M 256,260 Q 380,180 430,240 Q 380,310 270,300 Z'" \\
  -draw "path 'M 256,260 Q 132,180 82,240 Q 132,310 242,300 Z'" \\
  -stroke "#2c261e" -strokewidth 4 -fill none -draw "line 285,200 285,550 line 285,200 370,240 line 285,280 370,240" \\
  -font DejaVu-Sans-Bold -pointsize 42 -fill "#1f1c18" -stroke "#1f1c18" -strokewidth 1 -gravity north -annotate +0+45 "LINKIN PARK" \\
  -font DejaVu-Sans -pointsize 26 -fill "#822626" -gravity south -annotate +0+55 "HYBRID THEORY" \\
  "{linkin_park}"
""")

# PS1 Resident Evil Poster
ps1_re = os.path.join(TEX_DIR, "posters", "poster_ps1_resident_evil_style.png")
run(f"""
magick -size 512x768 xc:"#080a0c" \\
  -fill "#181a20" -draw "rectangle 0,0 512,80" \\
  -font DejaVu-Sans-Bold -pointsize 26 -fill "#f0f0f0" -gravity northwest -annotate +30+25 "PlayStation" \\
  \\( -size 472x480 gradient:"#1e0a0a-#080202" \\) -geometry +20+100 -composite \\
  -fill "#b01818" -stroke "#400" -strokewidth 4 \\
  -draw "circle 256,340 256,240" \\
  -fill "#f0f0f0" -draw "circle 256,340 256,300" \\
  -fill "#000000" -draw "circle 256,340 256,325" \\
  -font DejaVu-Serif-Bold -pointsize 52 -fill "#d01c1c" -stroke "#200" -strokewidth 2 -gravity south -annotate +0+110 "BIOHAZARD" \\
  -font DejaVu-Sans-Bold -pointsize 24 -fill "#a0a4a8" -gravity south -annotate +0+65 "SURVIVAL HORROR - 1998" \\
  "{ps1_re}"
""")

# PS1 Mecha Sci-Fi Poster
ps1_mecha = os.path.join(TEX_DIR, "posters", "poster_ps1_scifi_action.png")
run(f"""
magick -size 512x768 xc:"#0a0e16" \\
  -fill "#141a24" -draw "rectangle 0,0 512,80" \\
  -font DejaVu-Sans-Bold -pointsize 26 -fill "#f0f0f0" -gravity northwest -annotate +30+25 "PlayStation" \\
  \\( -size 472x480 gradient:"#102840-#081422" \\) -geometry +20+100 -composite \\
  -stroke "#20a8e8" -strokewidth 4 -fill "#144870" \\
  -draw "polygon 256,180 340,320 300,440 212,440 172,320" \\
  -stroke "#38d8ff" -strokewidth 3 -fill none \\
  -draw "circle 256,300 256,210 line 256,190 256,410 line 150,300 362,300" \\
  -font DejaVu-Sans-Bold -pointsize 44 -fill "#30d0ff" -gravity south -annotate +0+110 "ARMORED CORE" \\
  -font DejaVu-Sans-Mono -pointsize 20 -fill "#80c0d8" -gravity south -annotate +0+70 "TACTICAL MECHA SIMULATION" \\
  "{ps1_mecha}"
""")

# Skate Stickers Collage
stickers = os.path.join(TEX_DIR, "posters", "poster_skate_stickers.png")
run(f"""
magick -size 512x512 xc:"#18181c" \\
  \\( -size 200x200 xc:"#e62020" -fill "#ffaa00" -stroke "#000" -strokewidth 4 -draw "circle 100,100 100,20" -font DejaVu-Sans-Bold -pointsize 26 -fill "#000" -gravity center -annotate +0+0 "SPITFIRE" \\) -geometry +40+40 -composite \\
  \\( -size 220x150 xc:"#202020" -fill "#00d040" -stroke "#fff" -strokewidth 3 -draw "rectangle 10,10 210,140" -font DejaVu-Sans-Bold -pointsize 24 -fill "#fff" -gravity center -annotate +0+0 "TOY MACHINE" \\) -geometry +250+80 -composite \\
  \\( -size 240x160 xc:"#0060c0" -fill "#fff" -stroke "#000" -strokewidth 3 -draw "circle 120,80 120,20" -font DejaVu-Sans-Bold -pointsize 22 -fill "#e02020" -gravity center -annotate +0+0 "WORLD IND." \\) -geometry +70+290 -composite \\
  \\( -size 180x180 xc:"#282828" -stroke "#00f0d0" -strokewidth 5 -fill none -draw "circle 90,90 90,20" -font DejaVu-Sans-Bold -pointsize 20 -fill "#00f0d0" -gravity center -annotate +0+0 "ALIEN WORKSHOP" \\) -geometry +280+280 -composite \\
  "{stickers}"
""")

print("=== Generating Composite Wall Atlases ===")

# Atlas 1: Left Wall (Nu-Metal: Korn, Slipknot, stickers) 1024x1024
left_atlas = os.path.join(TEX_DIR, "atlases", "posters_nu_metal_left.png")
run(f"""
magick -size 1024x1024 xc:"#1c1a18" \\
  \\( -size 1024x1024 plasma:fractal -colorspace Gray -modulate 35,0,100 \\) -compose Multiply -composite \\
  \\( "{korn_issues}" -resize 440x660 \\) -geometry +50+40 -composite \\
  \\( "{slipknot_photo}" -resize 440x660 \\) -geometry +530+40 -composite \\
  \\( "{slipknot_top}" -resize 260x260 \\) -geometry +100+730 -composite \\
  \\( "{stickers}" -resize 260x260 \\) -geometry +660+730 -composite \\
  \\( "{korn_logo}" -resize 260x260 \\) -geometry +380+730 -composite \\
  "{left_atlas}"
""")

# Atlas 2: Center Wall (Deftones, Linkin Park, Gig Flyers) 1024x1024
center_atlas = os.path.join(TEX_DIR, "atlases", "posters_center_band.png")
run(f"""
magick -size 1024x1024 xc:"#1c1a18" \\
  \\( -size 1024x1024 plasma:fractal -colorspace Gray -modulate 35,0,100 \\) -compose Multiply -composite \\
  \\( "{deftones_white}" -resize 440x660 \\) -geometry +50+50 -composite \\
  \\( "{linkin_park}" -resize 440x660 \\) -geometry +530+50 -composite \\
  \\( "{deftones_band}" -resize 400x260 \\) -geometry +80+730 -composite \\
  \\( "{korn_logo}" -resize 400x260 \\) -geometry +540+730 -composite \\
  "{center_atlas}"
""")

# Atlas 3: Right Wall / Game Corner (PS1 games, mecha, anime) 1024x1024
right_atlas = os.path.join(TEX_DIR, "atlases", "posters_ps1_games.png")
run(f"""
magick -size 1024x1024 xc:"#1c1a18" \\
  \\( -size 1024x1024 plasma:fractal -colorspace Gray -modulate 35,0,100 \\) -compose Multiply -composite \\
  \\( "{ps1_re}" -resize 440x660 \\) -geometry +50+50 -composite \\
  \\( "{ps1_mecha}" -resize 440x660 \\) -geometry +530+50 -composite \\
  \\( "{stickers}" -resize 420x260 \\) -geometry +70+730 -composite \\
  \\( "{slipknot_photo}" -resize 420x260 \\) -geometry +530+730 -composite \\
  "{right_atlas}"
""")

print("=== Generating Props Textures ===")

# 1. Soda Can Colormap (256x256) for Kenney soda_can.obj and soda_can_crushed.obj
# Needs:
# U=0.96875, V=0.325..0.425 -> Coca-Cola Red with wave ribbon and barcode
# U=0.59375, V=0.025..0.225 -> Brushed silver aluminum
# U=0.53125, V=0.125 -> Dark aluminum tab
can_colormap = os.path.join(OBJ_TEX_DIR, "colormap.png")
can_prop = os.path.join(TEX_DIR, "props", "prop_soda_can_cola.png")
run(f"""
magick -size 256x256 xc:"#b8bcc0" \\
  \\( -size 128x256 xc:"#d41824" \\
     -stroke "#ffffff" -strokewidth 6 -fill none -draw "path 'M 0,160 Q 64,120 128,150'" \\
     -stroke "#e0e0e0" -strokewidth 2 -fill none -draw "path 'M 0,172 Q 64,132 128,162'" \\
     -font DejaVu-Sans-Bold -pointsize 24 -fill "#ffffff" -gravity north -annotate +0+25 "CLASSIC" \\
     -font DejaVu-Sans-Bold -pointsize 32 -fill "#ffffff" -gravity center -annotate +0-15 "COLA" \\
     -fill "#ffffff" -draw "rectangle 20,200 108,240" \\
     -fill "#000000" -draw "rectangle 26,206 32,234 rectangle 36,206 40,234 rectangle 46,206 54,234 rectangle 60,206 64,234 rectangle 70,206 78,234 rectangle 84,206 90,234 rectangle 96,206 102,234" \\
  \\) -geometry +128+0 -composite \\
  \\( -size 64x64 xc:"#70757a" -fill "#8a9096" -draw "circle 32,32 32,10" \\) -geometry +120+20 -composite \\
  "{can_colormap}"
""")
run(f"cp -f '{can_colormap}' '{can_prop}'")

# 2. PlayStation 1 Console Texture (1024x1024)
ps1_console_tex = os.path.join(TEX_DIR, "props", "prop_ps1_console.png")
run(f"""
magick -size 1024x1024 xc:"#a8aaae" \\
  \\( -size 1024x1024 xc:white +noise Gaussian -blur 0x0.5 -level 46%,54% \\) -compose Overlay -composite \\
  -stroke "#8e9094" -strokewidth 4 -fill "#9fa1a5" \\
  -draw "circle 512,480 512,180" \\
  -stroke "#828488" -strokewidth 3 -fill "#a4a6aa" \\
  -draw "circle 512,480 512,210" \\
  -stroke "#000000" -strokewidth 1 \\
  -fill "#d82424" -draw "polygon 500,430 524,430 524,480 500,480" \\
  -fill "#ffcc00" -draw "path 'M 490,480 Q 512,470 534,480 L 528,495 Q 512,485 496,495 Z'" \\
  -fill "#0088cc" -draw "path 'M 534,480 Q 550,500 524,520 L 514,505 Q 535,495 524,485 Z'" \\
  -fill "#00aa44" -draw "path 'M 490,480 Q 474,500 500,520 L 510,505 Q 489,495 500,485 Z'" \\
  -font DejaVu-Sans-Bold -pointsize 18 -fill "#3a3c40" -gravity northwest -annotate +180+220 "POWER" \\
  -stroke "#7c7e82" -strokewidth 3 -fill "#9a9c9f" -draw "circle 210,290 210,245" \\
  -fill "#10d040" -stroke none -draw "circle 210,348 210,342" \\
  -font DejaVu-Sans-Bold -pointsize 16 -fill "#3a3c40" -gravity northwest -annotate +200+690 "RESET" \\
  -stroke "#7c7e82" -strokewidth 3 -fill "#9a9c9f" -draw "circle 225,650 225,620" \\
  -font DejaVu-Sans-Bold -pointsize 18 -fill "#3a3c40" -gravity northeast -annotate +180+220 "OPEN" \\
  -stroke "#7c7e82" -strokewidth 3 -fill "#9a9c9f" -draw "circle 814,290 814,245" \\
  -stroke "#606266" -strokewidth 4 -fill "#25272a" \\
  -draw "roundrectangle 160,860 440,980 15,15" \\
  -draw "roundrectangle 584,860 864,980 15,15" \\
  -font DejaVu-Sans -pointsize 14 -fill "#808286" -gravity northwest -annotate +210+840 "MEMORY CARD - 1" \\
  -font DejaVu-Sans -pointsize 14 -fill "#808286" -gravity northeast -annotate +210+840 "MEMORY CARD - 2" \\
  -font DejaVu-Sans-Bold -pointsize 24 -fill "#2c2e32" -gravity south -annotate +0+165 "Sony Computer Entertainment" \\
  "{ps1_console_tex}"
""")

# 3. PS1 Controller Texture (512x512)
ps1_controller_tex = os.path.join(TEX_DIR, "props", "prop_ps1_controller.png")
run(f"""
magick -size 512x512 xc:"#a4a6aa" \\
  \\( -size 512x512 xc:white +noise Gaussian -blur 0x0.5 -level 46%,54% \\) -compose Overlay -composite \\
  -fill "#282a2e" -stroke "#1c1e22" -strokewidth 2 \\
  -draw "polygon 100,160 130,160 130,190 160,190 160,220 130,220 130,250 100,250 100,220 70,220 70,190 100,190" \\
  -fill "#282a2e" -stroke "#1c1e22" -strokewidth 2 \\
  -draw "circle 400,150 400,128" \\
  -draw "circle 450,200 450,178" \\
  -draw "circle 400,250 400,228" \\
  -draw "circle 350,200 350,178" \\
  -font DejaVu-Sans-Bold -pointsize 20 \\
  -fill "#10d060" -stroke none -gravity northwest -annotate +392+140 "▲" \\
  -fill "#e02030" -stroke none -gravity northwest -annotate +442+190 "●" \\
  -fill "#2070e0" -stroke none -gravity northwest -annotate +392+240 "✖" \\
  -fill "#e050b0" -stroke none -gravity northwest -annotate +342+190 "■" \\
  -fill "#303236" -stroke "#1c1e22" -strokewidth 1 \\
  -draw "roundrectangle 190,210 230,225 6,6" \\
  -draw "roundrectangle 282,210 322,225 6,6" \\
  -font DejaVu-Sans -pointsize 11 -fill "#404246" \\
  -gravity northwest -annotate +188+232 "SELECT" \\
  -gravity northwest -annotate +282+232 "START" \\
  -font DejaVu-Sans-Bold -pointsize 18 -fill "#2c2e32" -gravity center -annotate +0-70 "SONY" \\
  "{ps1_controller_tex}"
""")

# 4. PS1 Jewel Case Covers (512x512)
ps1_jewel = os.path.join(TEX_DIR, "props", "prop_cd_jewel_cases.png")
run(f"""
magick -size 512x512 xc:"#000000" \\
  -fill "#181818" -draw "rectangle 0,0 60,512" \\
  -stroke "#333333" -strokewidth 2 -fill none \\
  -draw "line 15,0 15,512 line 30,0 30,512 line 45,0 45,512" \\
  -fill "#0c1828" -draw "rectangle 64,0 512,512" \\
  -fill "#1a1a1a" -draw "rectangle 64,0 120,512" \\
  -font DejaVu-Sans-Bold -pointsize 20 -fill "#ffffff" \\
  -gravity northwest -annotate +74+80 "P" -annotate +74+105 "l" -annotate +74+125 "a" -annotate +74+145 "y" -annotate +74+165 "S" -annotate +74+185 "t" -annotate +74+205 "a" -annotate +74+225 "t" -annotate +74+245 "i" -annotate +74+265 "o" -annotate +74+285 "n" \\
  \\( -size 380x380 gradient:"#e62020-#101010" \\) -geometry +125+20 -composite \\
  -font DejaVu-Sans-Bold -pointsize 36 -fill "#ffffff" -stroke "#000" -strokewidth 2 -gravity northwest -annotate +150+90 "TONY HAWK" \\
  -font DejaVu-Sans-Bold -pointsize 26 -fill "#ffcc00" -stroke "#000" -strokewidth 1 -gravity northwest -annotate +150+140 "PRO SKATER 2" \\
  -fill "#000000" -draw "rectangle 125,410 505,500" \\
  -font DejaVu-Sans-Bold -pointsize 18 -fill "#e0e0e0" -gravity northwest -annotate +150+430 "ACTIVISION - NEVERSOFT" \\
  "{ps1_jewel}"
""")

# 5. Compact Disc Texture (512x512)
compact_disc_tex = os.path.join(TEX_DIR, "props", "prop_compact_disc.png")
run(f"""
magick -size 512x512 xc:none \\
  -fill "#c8ccd0" -stroke "#808590" -strokewidth 2 -draw "circle 256,256 256,12" \\
  \\( -size 512x512 gradient:"#ff0055-#00e0ff" -evaluate Sin 6 \\) -compose SoftLight -composite \\
  -fill none -stroke "#ffffff" -strokewidth 6 -draw "circle 256,256 256,36" \\
  -fill "#000000" -stroke none -draw "circle 256,256 256,180" \\
  -fill none -stroke "#606570" -strokewidth 4 -draw "circle 256,256 256,200" \\
  -font DejaVu-Sans-Bold -pointsize 16 -fill "#1a1c20" -gravity north -annotate +0+60 "COMPACT DISC DIGITAL AUDIO" \\
  "{compact_disc_tex}"
""")

# 6. Skateboard Complete Texture Atlas (1024x512)
# Left half (0..512): Grip Tape (coarse dithered black + bolts)
# Right half (512..1024): Deck Underside Graphic (flaming skull + nu-metal art)
# Top strips: Aluminum trucks and urethane wheels
skate_tex = os.path.join(TEX_DIR, "props", "prop_skateboard.png")
run(f"""
magick -size 1024x512 xc:"#121214" \\
  \\( -size 512x512 xc:"#181a1c" \\
     \\( -size 512x512 xc:white +noise Random -blur 0x0.5 -level 20%,80% \\) -compose Overlay -composite \\
     -stroke "#101012" -strokewidth 4 -fill none -draw "line 256,0 256,512" \\
     -fill "#b0b5ba" -stroke "#404246" -strokewidth 1 \\
     -draw "circle 230,90 230,86 circle 282,90 282,86 circle 230,140 230,136 circle 282,140 282,136" \\
     -draw "circle 230,370 230,366 circle 282,370 282,366 circle 230,420 230,416 circle 282,420 282,416" \\
  \\) -geometry +0+0 -composite \\
  \\( -size 512x512 xc:"#24140c" \\
     -fill "#d82418" -stroke "#600808" -strokewidth 6 -draw "circle 256,256 256,100" \\
     \\( -size 512x512 gradient:"#ffaa00-#e01010" -evaluate Sin 5 \\) -compose Overlay -composite \\
     -fill "#f2ece4" -stroke "#1c0a06" -strokewidth 5 \\
     -draw "circle 256,230 256,160" \\
     -draw "path 'M 216,260 L 216,320 L 296,320 L 296,260 Z'" \\
     -fill "#1c0a06" -stroke none \\
     -draw "circle 230,225 230,205 circle 282,225 282,205" \\
     -draw "polygon 256,245 250,265 262,265" \\
     -stroke "#1c0a06" -strokewidth 3 -fill none \\
     -draw "line 232,295 232,320 line 248,295 248,320 line 264,295 264,320 line 280,295 280,320" \\
     -font DejaVu-Sans-Bold -pointsize 32 -fill "#ffffff" -stroke "#000" -strokewidth 2 -gravity north -annotate +0+50 "ZERO" \\
     -font DejaVu-Sans-Bold -pointsize 26 -fill "#ffaa00" -stroke "#000" -strokewidth 2 -gravity south -annotate +0+60 "SKATEBOARDS" \\
  \\) -geometry +512+0 -composite \\
  "{skate_tex}"
""")
run(f"cp -f '{skate_tex}' '{os.path.join(TEX_DIR, 'atlases', 'skateboard.png')}'")

# 7. Electric Guitar Texture (1024x1024)
guitar_tex = os.path.join(TEX_DIR, "props", "prop_electric_guitar.png")
run(f"""
magick -size 1024x1024 xc:"#080808" \\
  \\( -size 512x700 gradient:"#e67818-#1c0800" \\) -geometry +256+200 -composite \\
  \\( -size 1024x1024 xc:black -fill none -stroke "#080808" -strokewidth 90 -draw "ellipse 512,600 280,320 0,360" \\) -compose Multiply -composite \\
  -fill "#eae4dc" -stroke "#202020" -strokewidth 3 \\
  -draw "path 'M 360,420 Q 420,380 500,420 Q 580,440 640,540 Q 640,680 540,780 Q 420,800 360,700 Z'" \\
  -fill "#181818" -stroke "#c8ccd0" -strokewidth 4 \\
  -draw "roundrectangle 460,480 564,530 8,8" \\
  -draw "roundrectangle 460,570 564,620 8,8" \\
  -fill "#b8bcc0" -stroke "#505458" -strokewidth 2 \\
  -draw "rectangle 450,680 574,720" \\
  -fill "#282a2e" -stroke "#b0b4b8" -strokewidth 2 \\
  -draw "circle 610,710 610,695 circle 610,760 610,745 circle 570,780 570,765" \\
  -fill "#3a2418" -stroke "#1c120c" -strokewidth 3 \\
  -draw "rectangle 488,50 536,440" \\
  -stroke "#d8dce0" -strokewidth 3 -fill none \\
  -draw "line 488,90 536,90 line 488,130 536,130 line 488,170 536,170 line 488,210 536,210 line 488,250 536,250 line 488,290 536,290 line 488,330 536,330 line 488,370 536,370 line 488,410 536,410" \\
  -fill "#f0f4f8" -stroke none \\
  -draw "circle 512,150 512,145 circle 512,230 512,225 circle 512,310 512,305 circle 504,390 504,386 circle 520,390 520,386" \\
  "{guitar_tex}"
""")

# 8. Band T-Shirt Graphic (512x512)
tshirt_tex = os.path.join(TEX_DIR, "props", "prop_band_shirt_skull.png")
run(f"""
magick -size 512x512 xc:"#1c1d20" \\
  \\( -size 512x512 xc:white +noise Gaussian -blur 0x0.5 -level 40%,60% \\) -compose Overlay -composite \\
  \\( -size 360x360 xc:none \\
     -stroke "#8a1010" -strokewidth 8 -fill "#c82020" -draw "circle 180,180 180,60" \\
     -fill "#f2eee8" -stroke "#180404" -strokewidth 5 \\
     -draw "circle 180,160 180,90" \\
     -draw "path 'M 140,190 L 140,260 L 220,260 L 220,190 Z'" \\
     -fill "#180404" -stroke none \\
     -draw "circle 155,160 155,140 circle 205,160 205,140" \\
     -draw "polygon 180,175 174,195 186,195" \\
     -font DejaVu-Sans-Bold -pointsize 32 -fill "#d82424" -stroke "#000" -strokewidth 2 -gravity north -annotate +0+25 "S L I P K N O T" \\
     -font DejaVu-Sans-Mono -pointsize 18 -fill "#f0f0f0" -gravity south -annotate +0+35 "DES MOINES, IOWA" \\
  \\) -geometry +76+76 -composite \\
  "{tshirt_tex}"
""")

# 9. CRT TV Chassis and Screen (512x512)
crt_chassis = os.path.join(TEX_DIR, "props", "prop_crt_tv_unit.png")
crt_screen = os.path.join(TEX_DIR, "props", "prop_crt_tv_screen.png")
crt_static = os.path.join(TEX_DIR, "props", "crt_tv_static_emissive.png")
run(f"""
magick -size 512x512 xc:"#202226" \\
  -fill "#362216" -draw "rectangle 0,0 70,512 rectangle 442,0 512,512" \\
  -fill "#181a1e" -draw "roundrectangle 80,40 432,380 30,30" \\
  -fill "#0c0d10" -draw "roundrectangle 96,56 416,364 25,25" \\
  -fill "#14161a" -draw "rectangle 80,400 432,490" \\
  -stroke "#2a2d32" -strokewidth 2 -fill none \\
  -draw "line 100,420 300,420 line 100,435 300,435 line 100,450 300,450 line 100,465 300,465" \\
  -fill "#40444c" -stroke "#101214" -strokewidth 2 \\
  -draw "circle 350,430 350,412 circle 395,430 395,412" \\
  -fill "#e02020" -draw "circle 350,470 350,464" \\
  "{crt_chassis}"
""")
run(f"cp -f '{crt_chassis}' '{os.path.join(TEX_DIR, 'atlases', 'crt_tv_unit.png')}'")

# Screen (inactive / diffuse)
run(f"""
magick -size 512x512 xc:"#10151c" \\
  \\( -size 512x512 gradient:"#203040-#080c10" \\) -compose Overlay -composite \\
  \\( -size 512x512 xc:white +noise Random -blur 0x0.5 -level 40%,60% \\) -compose Overlay -composite \\
  "{crt_screen}"
""")

# Emissive CRT static (blue-cyan glowing static + scanlines)
run(f"""
magick -size 512x512 xc:"#206090" \\
  \\( -size 512x512 xc:white +noise Random -level 20%,80% \\) -compose Overlay -composite \\
  \\( -size 512x512 gradient:"#80d8ff-#104060" \\) -compose Multiply -composite \\
  \\( -size 512x4 xc:black -size 512x4 xc:white -append -duplicate 63 -append \\) -compose Multiply -composite \\
  "{crt_static}"
""")

# 10. Beige CRT PC Monitor (512x512)
pc_monitor = os.path.join(TEX_DIR, "props", "prop_retro_pc_monitor.png")
run(f"""
magick -size 512x512 xc:"#d8d4c8" \\
  \\( -size 512x512 xc:white +noise Gaussian -blur 0x0.5 -level 46%,54% \\) -compose Overlay -composite \\
  -fill "#141c18" -stroke "#a8a498" -strokewidth 12 \\
  -draw "roundrectangle 60,40 452,400 20,20" \\
  -stroke "#787468" -strokewidth 3 -fill none \\
  -draw "line 80,430 240,430 line 80,445 240,445" \\
  -fill "#40e040" -stroke "#202020" -strokewidth 2 -draw "circle 420,440 420,432" \\
  -font DejaVu-Sans-Bold -pointsize 18 -fill "#303030" -gravity northwest -annotate +80+460 "SYNCMASTER" \\
  "{pc_monitor}"
""")
run(f"cp -f '{pc_monitor}' '{os.path.join(TEX_DIR, 'atlases', 'crt_monitor_pc.png')}'")

# 11. Retro PC Desktop Screen (512x512)
pc_screen = os.path.join(TEX_DIR, "props", "prop_pc_screen_display.png")
run(f"""
magick -size 512x512 xc:"#008080" \\
  -fill "#c0c0c0" -draw "rectangle 0,480 512,512" \\
  -stroke "#ffffff" -strokewidth 2 -fill "#c0c0c0" -draw "rectangle 2,482 70,510" \\
  -font DejaVu-Sans-Bold -pointsize 14 -fill "#000000" -gravity southwest -annotate +15+10 "Start" \\
  -fill "#000080" -draw "rectangle 40,40 472,66" \\
  -fill "#c0c0c0" -draw "rectangle 40,66 472,420" \\
  -font DejaVu-Sans-Bold -pointsize 14 -fill "#ffffff" -gravity northwest -annotate +50+45 "WINAMP - Slipknot - Wait and Bleed.mp3" \\
  -fill "#000000" -draw "rectangle 60,80 452,180" \\
  \\( -size 380x80 xc:black -stroke "#00ff00" -strokewidth 2 -draw "line 0,40 40,10 line 40,10 80,70 line 80,70 120,20 line 120,20 160,50 line 160,50 200,10 line 200,10 240,65 line 240,65 280,30 line 280,30 320,60 line 320,60 380,40" \\) -geometry +65+90 -composite \\
  -font DejaVu-Sans-Mono -pointsize 28 -fill "#00ff00" -gravity northwest -annotate +70+135 "02:27 [KBPS: 128]" \\
  "{pc_screen}"
""")

# 12. PC Tower (512x512)
pc_tower = os.path.join(TEX_DIR, "props", "prop_pc_tower.png")
run(f"""
magick -size 512x512 xc:"#d8d4c8" \\
  \\( -size 512x512 xc:white +noise Gaussian -blur 0x0.5 -level 46%,54% \\) -compose Overlay -composite \\
  -fill "#c4c0b4" -stroke "#989488" -strokewidth 3 \\
  -draw "rectangle 40,40 472,120" \\
  -fill "#181818" -draw "rectangle 60,65 400,75" \\
  -fill "#a09c90" -draw "rectangle 420,80 450,100" \\
  -font DejaVu-Sans -pointsize 12 -fill "#404040" -gravity northwest -annotate +60+45 "52x CD-ROM MAX" \\
  -fill "#c4c0b4" -stroke "#989488" -strokewidth 3 \\
  -draw "rectangle 40,140 472,210" \\
  -fill "#181818" -draw "rectangle 100,165 380,172" \\
  -fill "#a09c90" -draw "rectangle 410,175 440,195" \\
  -fill "#40e040" -stroke "#202020" -strokewidth 1 -draw "circle 80,185 80,180" \\
  -font DejaVu-Sans -pointsize 12 -fill "#404040" -gravity northwest -annotate +60+145 "3.5 INCH FLOPPY DISK" \\
  -fill "#303030" -stroke "#101010" -strokewidth 2 -draw "rectangle 60,320 120,380" \\
  -font DejaVu-Sans-Bold -pointsize 14 -fill "#ffffff" -gravity northwest -annotate +66+345 "POWER" \\
  -fill "#000000" -draw "rectangle 380,320 450,380" \\
  -font DejaVu-Sans-Mono-Bold -pointsize 32 -fill "#ffaa00" -gravity northwest -annotate +390+332 "66" \\
  "{pc_tower}"
""")

# 13. Retro Laptop Base and Lid (512x512)
laptop_base = os.path.join(TEX_DIR, "props", "prop_retro_laptop_base.png")
laptop_lid = os.path.join(TEX_DIR, "props", "prop_retro_laptop_lid.png")
run(f"""
magick -size 512x512 xc:"#28292c" \\
  -fill "#1a1b1d" -stroke "#121214" -strokewidth 2 \\
  -draw "roundrectangle 40,40 472,310 10,10" \\
  \\( -size 420x250 xc:"#121315" \\
     \\( -size 30x20 xc:"#2c2d30" -stroke "#121214" -strokewidth 1 \\) -duplicate 13 -append \\
  \\) -geometry +46+50 -composite \\
  -fill "#d82424" -stroke none -draw "circle 256,190 256,182" \\
  -fill "#202124" -stroke "#121214" -strokewidth 2 \\
  -draw "roundrectangle 210,340 302,400 8,8" \\
  -draw "line 256,340 256,400" \\
  -font DejaVu-Sans-Bold -pointsize 18 -fill "#f0f0f0" -gravity southeast -annotate +40+40 "ThinkPad" \\
  "{laptop_base}"
""")
run(f"""
magick -size 512x512 xc:"#242528" \\
  -font DejaVu-Sans-Bold -pointsize 36 -fill "#f0f0f0" -stroke "#000" -strokewidth 1 \\
  -gravity southeast -annotate +60+60 "IBM" \\
  -fill "#d82424" -stroke none -draw "circle 450,440 450,432" \\
  "{laptop_lid}"
""")

# 14. Boombox Stereo (512x512)
boombox_tex = os.path.join(TEX_DIR, "props", "prop_boombox_radio.png")
run(f"""
magick -size 512x512 xc:"#26282c" \\
  -fill "#16181b" -stroke "#0e0f11" -strokewidth 3 \\
  -draw "circle 130,280 130,180 circle 382,280 382,180" \\
  \\( -size 180x180 xc:black -stroke "#333840" -strokewidth 2 -fill none -draw "circle 90,90 90,70 circle 90,90 90,50 circle 90,90 90,30" \\) -geometry +40+190 -composite \\
  \\( -size 180x180 xc:black -stroke "#333840" -strokewidth 2 -fill none -draw "circle 90,90 90,70 circle 90,90 90,50 circle 90,90 90,30" \\) -geometry +292+190 -composite \\
  -fill "#141518" -stroke "#40444c" -strokewidth 2 \\
  -draw "rectangle 230,220 282,340" \\
  -fill "#282a2e" -draw "circle 246,260 246,252 circle 266,260 266,252" \\
  -fill "#ffaa00" -draw "rectangle 120,70 392,120" \\
  -font DejaVu-Sans-Mono -pointsize 16 -fill "#101010" -gravity northwest -annotate +130+80 "FM 88 . 92 . 96 . 100 . 104 . 108" \\
  -font DejaVu-Sans-Mono -pointsize 14 -fill "#101010" -gravity northwest -annotate +130+100 "AM 530 . 700 . 900 . 1200 . 1600" \\
  -font DejaVu-Sans-Bold -pointsize 20 -fill "#e0e0e0" -gravity north -annotate +0+30 "BASS REFLEX STEREO" \\
  "{boombox_tex}"
""")
run(f"cp -f '{boombox_tex}' '{os.path.join(TEX_DIR, 'atlases', 'desk_boombox_audio.png')}'")

# 15. Bookshelf Speaker (512x512)
speaker_tex = os.path.join(TEX_DIR, "props", "prop_speaker.png")
run(f"""
magick -size 512x512 xc:"#322014" \\
  -fill "#181a1d" -stroke "#101113" -strokewidth 8 \\
  -draw "roundrectangle 40,40 472,472 20,20" \\
  \\( -size 420x420 xc:black +noise Random -blur 0x0.5 -level 20%,80% \\) -geometry +46+46 -composite \\
  -fill "#0c0d0f" -stroke "#303236" -strokewidth 4 \\
  -draw "circle 256,170 256,110 circle 256,330 256,240" \\
  -fill "#d8b024" -stroke none -draw "rectangle 240,430 272,442" \\
  "{speaker_tex}"
""")

# 16. Studio Headphones (512x512)
headphones_tex = os.path.join(TEX_DIR, "props", "prop_headphones.png")
run(f"""
magick -size 512x512 xc:"#1c1d20" \\
  \\( -size 512x512 xc:white +noise Gaussian -blur 0x0.5 -level 40%,60% \\) -compose Overlay -composite \\
  -fill "#26282c" -stroke "#121315" -strokewidth 6 \\
  -draw "circle 160,256 160,140 circle 352,256 352,140" \\
  -fill "#0e0f11" -stroke "#343840" -strokewidth 8 \\
  -draw "circle 160,256 160,190 circle 352,256 352,190" \\
  -stroke "#888c94" -strokewidth 12 -fill none \\
  -draw "path 'M 160,120 Q 256,40 352,120'" \\
  -font DejaVu-Sans-Bold -pointsize 18 -fill "#a0a4ac" -gravity north -annotate +0+50 "STUDIO MONITOR" \\
  "{headphones_tex}"
""")

# 17. Corrugated Cardboard Box (512x512)
box_tex = os.path.join(TEX_DIR, "props", "prop_cardboard_box.png")
run(f"""
magick -size 512x512 xc:"#b08854" \\
  \\( -size 512x512 plasma:fractal -colorspace Gray -modulate 40,0,100 \\) -compose Multiply -composite \\
  \\( -size 512x80 xc:"#d0b888" -evaluate Sine 8 \\) -geometry +0+216 -compose SoftLight -composite \\
  -fill "#f2f2f2" -stroke "#333" -strokewidth 2 \\
  -draw "rectangle 60,60 260,190" \\
  -fill "#000000" -stroke none \\
  -draw "rectangle 80,80 86,140 rectangle 92,80 96,140 rectangle 102,80 110,140 rectangle 116,80 120,140 rectangle 126,80 134,140 rectangle 140,80 148,140 rectangle 154,80 162,140 rectangle 168,80 172,140 rectangle 178,80 186,140 rectangle 192,80 196,140" \\
  -font DejaVu-Sans-Bold -pointsize 16 -fill "#000000" -gravity northwest -annotate +80+150 "FRAGILE" \\
  -fill "#d82424" -stroke none -draw "rectangle 280,60 450,110" \\
  -font DejaVu-Sans-Bold -pointsize 20 -fill "#ffffff" -gravity northwest -annotate +290+72 "THIS SIDE UP" \\
  "{box_tex}"
""")

# 18. Digital Clock Emissive LED "12:07" (512x256)
clock_led = os.path.join(TEX_DIR, "emissive", "clock_led_1207_emissive.png")
clock_prop = os.path.join(TEX_DIR, "props", "prop_alarm_clock_1207.png")
run(f"""
magick -size 512x256 xc:"#000000" \\
  -font DejaVu-Sans-Mono-Bold -pointsize 130 -fill "#ff1a1a" \\
  -gravity center -annotate +0+0 "12:07" \\
  \\( -size 512x256 xc:none -font DejaVu-Sans-Mono-Bold -pointsize 130 -fill "#ff4444" -gravity center -annotate +0+0 "12:07" -blur 0x12 \\) \\
  -compose Plus -composite \\
  "{clock_led}"
""")
run(f"""
magick -size 512x256 xc:"#1c1d20" \\
  -fill "#0c0d0e" -stroke "#383a40" -strokewidth 6 \\
  -draw "roundrectangle 40,30 472,226 20,20" \\
  \\( "{clock_led}" -resize 390x150 \\) -geometry +61+53 -composite \\
  "{clock_prop}"
""")

# 19. Bedside Lamp (512x512)
lamp_tex = os.path.join(TEX_DIR, "props", "prop_nightstand_lamp.png")
run(f"""
magick -size 512x512 xc:"#d4a844" \\
  \\( -size 512x512 gradient:"#ffdd88-#886611" \\) -compose Overlay -composite \\
  -fill "#f2ede0" -stroke "#a89e8c" -strokewidth 4 \\
  -draw "polygon 100,240 412,240 372,40 140,40" \\
  \\( -size 512x512 xc:white +noise Random -blur 0x1 -level 40%,60% \\) -compose Overlay -composite \\
  "{lamp_tex}"
""")

# 20. Wire Mesh Trashcan (512x512)
trash_tex = os.path.join(TEX_DIR, "props", "prop_trashcan.png")
run(f"""
magick -size 8x8 xc:black -stroke "#484d54" -strokewidth 1 -draw "line 0,0 8,8 line 8,0 0,8" /tmp/trash_pat.png
magick -size 512x512 tile:/tmp/trash_pat.png \\
  -stroke "#6c727c" -strokewidth 12 -fill none -draw "rectangle 10,10 502,502" \\
  "{trash_tex}"
rm -f /tmp/trash_pat.png
""")

print("=== ALL TEXTURES GENERATED SUCCESSFULLY ===")
