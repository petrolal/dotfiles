package polyomino.dotfiles.pickers

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.ThemeEngine

object WofiPickers:

  private def getDistroSubtitle(): String =
    val osName = try
      if os.exists(os.root / "etc" / "os-release") then
        os.read.lines(os.root / "etc" / "os-release")
          .find(l => l.startsWith("NAME=") || l.startsWith("PRETTY_NAME="))
          .map(_.split("=", 2)(1).replace("\"", "").replace("'", "").trim)
          .getOrElse("Linux")
      else "Linux"
    catch
      case _: Exception => "Linux"
    s"$osName · SwayFX"

  /** Path to the shared GTK tile-menu script (grid of tiles, glass/blur
    * backdrop) that all pickers below use in place of a wofi dmenu list. */
  private def tilemenuScript(ctx: Context): os.Path =
    val script = ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-tilemenu.py"
    if os.exists(script) then script else ctx.configDir / "sway" / "scripts" / "polyomino-tilemenu.py"

  private def tile(id: String, icon: String, title: String, desc: String, accent: String, badge: Option[String] = None, variant: String = "card"): ujson.Value =
    val obj = ujson.Obj("id" -> id, "icon" -> icon, "title" -> title, "desc" -> desc, "accent" -> accent, "variant" -> variant)
    badge.foreach(b => obj("badge") = b)
    obj

  /** Launches the shared tile-menu GTK window with the given tiles and
    * returns the selected tile's id, or "" if the window was cancelled. */
  private def tilePick(
    ctx: Context,
    title: String,
    tiles: Seq[ujson.Value],
    columns: Int,
    width: Int,
    height: Int,
    info: Boolean = false,
    subtitle: String = getDistroSubtitle(),
    badge: String = "MENU"
  ): String =
    val script = tilemenuScript(ctx)
    val args: Seq[os.Shellable] = (Seq(
      "python3", script.toString,
      "--title", title,
      "--subtitle", subtitle,
      "--badge", badge,
      "--columns", columns.toString,
      "--width", width.toString,
      "--height", height.toString
    ) ++ (if info then Seq("--info") else Seq.empty)).map(s => (s: os.Shellable))
    val res = os.proc(args*).call(stdin = ujson.write(tiles), check = false)
    res.out.text().trim

  def runLauncher(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    val script = tilemenuScript(ctx)
    try
      val cmd: Seq[os.Shellable] = Seq("python3", script.toString, "--drun").map(s => (s: os.Shellable))
      os.proc(cmd*).call(check = false)
      Right(())
    catch
      case e: Exception => Left(CommandError(s"Launcher failed: ${e.getMessage}"))

  def runThemePicker(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle behavior: check if theme picker is already open
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*Theme").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    println(" [1;35m[polyomino theme-picker] [0m Launching tile GUI theme picker...")

    val themes = polyomino.dotfiles.theme.Palette.listAll(ctx)
    val activePalette = ThemeEngine.getActivePalette(ctx)

    try
      // Step 1: Select Theme
      val themeTiles = themes.map { name =>
        val pal = polyomino.dotfiles.theme.Palette.find(name, ctx)
        val isActive = pal.name.equalsIgnoreCase(activePalette.name)
        tile(pal.name, "◆", pal.name.capitalize, pal.label, "accent", badge = if isActive then Some("ACTIVE") else None)
      }
      val selectedTheme = tilePick(ctx, "POLYOMINO // THEME PICKER", themeTiles, columns = 2, width = 640, height = 400, badge = "THEMES")
      if selectedTheme.isEmpty then return Right(())

      println(s"   [32m[OK] [0m Selected theme '$selectedTheme'")

      // Step 2: Select Wallpaper Mode
      val modeTiles = Seq(
        tile("static", "▣", "Static Wallpaper", "Keep a single wallpaper for this flavor", "teal"),
        tile("rotate", "↻", "Rotate Wallpapers", "Cycle through the flavor's wallpapers every 30m", "sapphire")
      )
      val mode = tilePick(ctx, s"POLYOMINO // MODE · ${selectedTheme.capitalize}", modeTiles, columns = 2, width = 560, height = 300, badge = "MODE") match
        case "rotate" => "rotate"
        case "static" => "wallpaper"
        case _ => return Right(())
      val interval = "30m"

      // Step 3 (static mode only): if the chosen flavor ships more than one
      // wallpaper, let the user pick which one instead of silently taking the
      // first. "Auto" keeps the previous behaviour.
      val customWallpaper: Option[String] =
        if mode != "wallpaper" then None
        else
          val palName = polyomino.dotfiles.theme.Palette.find(selectedTheme, ctx).name
          val choices = polyomino.dotfiles.wallpaper.WallpaperEngine.wallpapersForFlavor(ctx, palName)
          if choices.size <= 1 then None
          else
            val AutoId = "__auto__"
            val wpTiles = tile(AutoId, "◆", "Auto (Default)", "Let polyomino pick the flavor's default", "accent") +:
              choices.map(p => tile(p.toString, "🖼", p.last, "", "peach"))
            val picked = tilePick(ctx, s"POLYOMINO // WALLPAPER · ${selectedTheme.capitalize}", wpTiles, columns = 3, width = 720, height = 460, badge = "WALLPAPERS")
            if picked.isEmpty || picked == AutoId then None
            else Some(picked)

      ThemeEngine.applyTheme(ctx, selectedTheme, mode = mode, customWallpaper = customWallpaper, interval = interval)
    catch
      case e: Exception => Left(CommandError(s"Theme-picker failed: ${e.getMessage}"))

  def runWallpaperPicker(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    import polyomino.dotfiles.wallpaper.WallpaperEngine

    // Toggle behavior: close an already-open wallpaper picker
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*Wallpaper").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val flavor = WallpaperEngine.activeFlavor(ctx)
    val options = WallpaperEngine.wallpapersForFlavor(ctx, flavor)
    if options.isEmpty then
      return Left(CommandError(s"No wallpapers for flavor '$flavor' in themes/wallpapers/"))

    println(s" [1;35m[polyomino wallpaper-picker] [0m Launching tile GUI wallpaper picker for '$flavor'...")

    val current = WallpaperEngine.currentWallpaper(ctx)
    val RandomId = "__random__"
    val tiles = tile(RandomId, "🎲", "Random", "Cycle to a random wallpaper", "mauve") +: options.map { p =>
      val isActive = current.contains(p.toString)
      tile(p.toString, "🖼", p.last, "", "peach", badge = if isActive then Some("ACTIVE") else None)
    }

    try
      val selected = tilePick(ctx, s"POLYOMINO // WALLPAPERS · ${flavor.capitalize}", tiles, columns = 3, width = 720, height = 460, badge = "WALLPAPERS")
      if selected.isEmpty then return Right(())
      if selected == RandomId then
        WallpaperEngine.run(ctx, List("random"))
      else
        val path = os.Path(selected)
        WallpaperEngine.run(ctx, List(path.baseName))
    catch
      case e: Exception => Left(CommandError(s"Wallpaper-picker failed: ${e.getMessage}"))

  /** `polyomino menu` — the click target for the waybar POLYOMINO pill.
    *
    * A tile-menu GTK window that fans out to the desktop's own tools: the Tetris
    * power menu, the theme/wallpaper pickers, an "edit a config file" sub-grid,
    * and the healthcheck. Everything it launches is an existing subcommand or
    * `polyomino-*` alias — the menu is pure front-end. Re-invoking it while it
    * is open closes it (matches the calendar / picker toggle behaviour). */
  def runMenu(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle: a second click closes the open menu.
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*POLYOMINO").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val term = sys.env.get("TERMINAL").filter(_.nonEmpty).getOrElse("kitty")
    val binDir = ctx.home / ".local" / "bin"
    val polyomino = (binDir / "polyomino").toString

    val outputArgs: Seq[String] =
      args.sliding(2).collectFirst {
        case Seq("--output" | "-o", name) if name.nonEmpty => Seq("-o", name)
      }.getOrElse(Seq.empty)

    val entries = Seq(
      tile("apps", "🚀", "App Launcher", "Launch applications & tools", "accent", variant = "square"),
      tile("welcome", "✨", "Welcome center", "Workstation quick-start hub", "teal", variant = "square"),
      tile("whichkey", "⌨", "Keybindings", "SwayFX cheatsheet", "blue", variant = "square"),
      tile("gamemode", "🎮", "Game mode", "Toggle performance mode", "green", variant = "square"),
      tile("power", "⏻", "Power menu", "Session power & reboot", "red", variant = "square"),
      tile("theme", "🎨", "Theme & Palette", "Switch flavor & accents", "mauve", variant = "square"),
      tile("wallpaper", "🖼", "Wallpaper", "Curated desktop backgrounds", "peach", variant = "square"),
      tile("edit", "⚙", "Edit a config", "Modify desktop configuration", "sapphire", variant = "square"),
      tile("health", "🩺", "Healthcheck", "Diagnostics & verification", "teal", variant = "square")
    )

    val hasSetsid = os.proc("sh", "-c", "command -v setsid").call(check = false).exitCode == 0
    def spawn(cmd: Seq[String]): Unit =
      val full = if hasSetsid then "setsid" +: cmd else cmd
      val shellable: Seq[os.Shellable] = full.map(s => (s: os.Shellable))
      os.proc(shellable*).spawn(stdout = os.Inherit, stderr = os.Inherit)

    try
      tilePick(ctx, "POLYOMINO // MAIN MENU", entries, columns = 3, width = 720, height = 360, badge = "DRAWER") match
        case "" => Right(())
        case "apps" =>
          spawn(Seq(polyomino, "launcher"))
          Right(())
        case "whichkey" =>
          spawn(Seq(polyomino, "whichkey"))
          Right(())
        case "welcome" =>
          spawn(Seq((binDir / "polyomino-welcome").toString))
          Right(())
        case "gamemode" =>
          spawn(Seq(polyomino, "gamemode", "toggle"))
          Right(())
        case "power" =>
          spawn(Seq(term, "--class=polyomino-power", "-o", "initial_window_width=80c", "-o", "initial_window_height=36c", "-o", "font_size=12", "-e", polyomino, "power-menu"))
          Right(())
        case "theme" =>
          spawn(Seq((binDir / "polyomino-theme-picker").toString) ++ outputArgs)
          Right(())
        case "wallpaper" =>
          spawn(Seq((binDir / "polyomino-wallpaper-picker").toString) ++ outputArgs)
          Right(())
        case "health" =>
          spawn(Seq(term, "-e", "sh", "-c", s"'$polyomino' healthcheck; printf '\\n[enter to close] '; read _"))
          Right(())
        case "edit" =>
          runConfigPicker(ctx, args)
        case _ => Right(())
    catch
      case e: Exception => Left(CommandError(s"Menu failed: ${e.getMessage}"))

  def runConfigPicker(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    // Toggle: close if already open
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*EDIT CONFIG").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val term = sys.env.get("TERMINAL").filter(_.nonEmpty).getOrElse("kitty")
    val editor = sys.env.get("EDITOR").filter(_.nonEmpty)
      .orElse(Some("nvim").filter(e => os.proc("sh", "-c", s"command -v $e").call(check = false).exitCode == 0))
      .getOrElse("vi")
    val candidates = Seq(
      ("sway/config", "sway/config", "Sway Window Manager", "sky"),
      ("waybar/config.jsonc", "waybar/config.jsonc", "Waybar Bar Layout", "teal"),
      ("waybar/modules.jsonc", "waybar/modules.jsonc", "Waybar Modules", "teal"),
      ("waybar/style.css", "waybar/style.css", "Waybar Stylesheet", "teal"),
      ("kitty/kitty.conf", "kitty/kitty.conf", "Kitty Terminal", "green"),
      ("swaync/config.json", "swaync/config.json", "SwayNC Notification Center", "sapphire"),
      ("mako/config", "mako/config", "Mako Notifications", "sapphire"),
      ("dunst/dunstrc", "dunst/dunstrc", "Dunst Notifications", "sapphire"),
      ("gamemode/gamemode.ini", "gamemode/gamemode.ini", "GameMode Settings", "green"),
      ("MangoHud/MangoHud.conf", "MangoHud/MangoHud.conf", "MangoHud Overlay", "peach"),
      ("fastfetch/config.jsonc", "fastfetch/config.jsonc", "Fastfetch System Info", "mauve")
    ).map { case (id, rel, desc, accent) => (id, ctx.configDir / os.RelPath(rel), desc, accent) }
      ++ Seq(("zsh/.zshrc", ctx.dotfilesDir / "zsh" / ".zshrc", "Zsh Shell Config", "peach"))
    val existing = candidates.filter { case (_, p, _, _) => os.exists(p) }
    if existing.isEmpty then Right(())
    else
      val configTiles = existing.map { case (id, _, desc, accent) => tile(id, "⚙", id, desc, accent) }
      val chosenId = tilePick(ctx, "POLYOMINO // EDIT CONFIG", configTiles, columns = 2, width = 640, height = 440, badge = "CONFIG")
      existing.find(_._1 == chosenId) match
        case Some((_, path, _, _)) =>
          val hasSetsid = os.proc("sh", "-c", "command -v setsid").call(check = false).exitCode == 0
          val cmd = Seq(term, "-e", editor, path.toString)
          val full = if hasSetsid then "setsid" +: cmd else cmd
          val shellable: Seq[os.Shellable] = full.map(s => (s: os.Shellable))
          os.proc(shellable*).spawn(stdout = os.Inherit, stderr = os.Inherit)
          Right(())
        case None => Right(())

  def runWhichkey(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle behavior: check if whichkey is already running
    try
      val checkRes = os.proc("pgrep", "-f", "(polyomino-tilemenu.*WHICH-KEY|rofi.*whichkey|wofi.*which-key)").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    println(" [1;35m[polyomino whichkey] [0m Displaying Sway keybindings cheatsheet...")
    val keybindingsList = resolveSwayKeybindings(ctx)
    val entries = if keybindingsList.nonEmpty then keybindingsList else defaultKeybindings

    val script = tilemenuScript(ctx)
    if os.exists(script) then
      val tiles = entries.zipWithIndex.map { case (line, idx) =>
        val parts = line.split("→", 2)
        val key = parts.headOption.getOrElse(line).trim
        val action = if parts.length > 1 then parts(1).trim else ""
        tile(idx.toString, "⌨", action, "", "accent", badge = Some(key))
      }
      try
        tilePick(
          ctx,
          title = "POLYOMINO // WHICH-KEY",
          tiles = tiles,
          columns = 2,
          width = 980,
          height = 560,
          info = true,
          subtitle = getDistroSubtitle(),
          badge = "SHORTCUTS"
        )
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Whichkey failed: ${e.getMessage}"))
    else
      Left(CommandError("polyomino-tilemenu.py script missing"))

  def runProjects(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    // Toggle behavior: check if project picker is already open
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*PROJECTS").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val projectDirs = collectProjectDirectories(ctx)
    if projectDirs.isEmpty then return Right(())

    val accents = Array("blue", "teal", "green", "peach", "mauve", "sapphire", "sky", "yellow")
    val homeStr = ctx.home.toString

    val tiles = projectDirs.zipWithIndex.map { case (path, idx) =>
      val display = if path.startsWith(homeStr) then path.replace(homeStr, "~") else path
      val title = os.Path(path).last
      tile(path, "⊞", title, display, accents(idx % accents.length))
    }

    try
      val selected = tilePick(ctx, "[ ⊞ ] PROJECTS", tiles, columns = 3, width = 760, height = 520, badge = "PROJECTS")
      if selected.nonEmpty && os.exists(os.Path(selected)) then
        val term = if isCommandAvailable("kitty") then "kitty" else sys.env.getOrElse("TERMINAL", "kitty")
        val cmd: Seq[os.Shellable] = if term == "kitty" then
          Seq("kitty", "--class", "nvim-project", "-d", selected, "nvim", ".")
        else
          Seq(term, "-e", "nvim", selected)
        try os.proc(cmd*).spawn(stdout = os.Inherit, stderr = os.Inherit)
        catch case _: Exception => ()
      Right(())
    catch
      case e: Exception => Left(CommandError(s"Project picker failed: ${e.getMessage}"))

  private def isCommandAvailable(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0 catch case _: Exception => false

  private def collectProjectDirectories(ctx: Context): Seq[String] =
    val searchRoots = Seq(
      ctx.home / "Projects",
      ctx.dotfilesDir,
      ctx.home / "tetravim.nvim",
      ctx.configDir,
      ctx.home / "dev",
      ctx.home / "code",
      ctx.home / "src",
      ctx.home / "workspace",
      ctx.home / "repos",
      ctx.home / "Documents" / "Projects"
    ).filter(os.exists)

    val gitFound = scala.collection.mutable.Set[String]()

    if isCommandAvailable("fd") then
      try
        val rootArgs = searchRoots.map(_.toString)
        val fdCmd: Seq[os.Shellable] = (Seq("fd", "-H", "^\\.git$", "-t", "d", "-d", "5") ++ rootArgs).map(s => (s: os.Shellable))
        val fdRes = os.proc(fdCmd*).call(check = false)
        if fdRes.exitCode == 0 then
          for line <- fdRes.out.lines() do
            val trimmed = line.trim
            if trimmed.nonEmpty then
              val repoDir = os.Path(trimmed) / os.up
              if os.exists(repoDir) then gitFound += repoDir.toString
      catch
        case _: Exception => ()

    if gitFound.isEmpty then
      for root <- searchRoots do
        try
          os.walk(root, maxDepth = 5, skip = (p: os.Path) => p.last == ".git" || p.last == "target" || p.last == "node_modules").foreach { p =>
            if os.isDir(p) && os.exists(p / ".git") then
              gitFound += p.toString
          }
        catch
          case _: Exception => ()

    val projectsDir = ctx.home / "Projects"
    if os.exists(projectsDir) then
      try
        os.list(projectsDir).filter(os.isDir).foreach { p =>
          gitFound += p.toString
          try os.list(p).filter(os.isDir).foreach(sub => gitFound += sub.toString) catch case _: Exception => ()
        }
      catch
        case _: Exception => ()

    Seq(ctx.dotfilesDir, ctx.home / "tetravim.nvim", ctx.configDir).filter(os.exists).foreach(p => gitFound += p.toString)

    gitFound.toSeq.sorted

  private def resolveSwayKeybindings(ctx: Context): Seq[String] =
    try
      val configContent = getSwayConfigContent(ctx)
      if configContent.nonEmpty then expandBindings(configContent) else Nil
    catch
      case _: Exception => Nil

  private def getSwayConfigContent(ctx: Context): String =
    try
      val res = os.proc("swaymsg", "-t", "get_config").call(check = false)
      if res.exitCode == 0 then
        val json = ujson.read(res.out.text())
        json.obj.get("config").map(_.str).getOrElse("")
      else
        val configFile = ctx.configDir / "sway" / "config"
        if os.exists(configFile) then os.read(configFile) else ""
    catch
      case _: Exception =>
        val configFile = ctx.configDir / "sway" / "config"
        if os.exists(configFile) then os.read(configFile) else ""

  def expandBindings(config: String): Seq[String] =
    var varDefs = Map.empty[String, String]
    var bindings = Vector.empty[String]
    var inSubmode = false
    val seenActions = scala.collection.mutable.Set[String]()

    for rawLine <- config.linesIterator do
      // Strip comments from end of line
      val line = rawLine.takeWhile(_ != '#').trim
      if line.startsWith("mode ") && line.endsWith("{") then
        inSubmode = true
      else if inSubmode && line == "}" then
        inSubmode = false
      else if !inSubmode && line.nonEmpty then
        if line.startsWith("set ") then
          val parts = line.split("\\s+", 3)
          if parts.length >= 3 && parts(1).startsWith("$") then
            varDefs += (parts(1) -> parts(2).replace("\"", ""))
        else if line.startsWith("bindsym ") || line.startsWith("bindcode ") then
          var bindingLine = line
          bindingLine = bindingLine.replaceFirst("^(bindsym|bindcode)\\s+", "")
          while bindingLine.startsWith("--") do
            bindingLine = bindingLine.replaceFirst("^--[a-z-]+\\s+", "")

          for (varName, varVal) <- varDefs do
            bindingLine = bindingLine.replace(varName, varVal)

          val parts = bindingLine.split("\\s+", 2)
          if parts.length == 2 then
            val key = parts(0).trim
            val action = parts(1).trim

            // Skip raw numerical fallback keycodes (e.g. "107", "Mod4+61", "Mod4+193")
            val isRawCodeOnly = key.matches("^\\d+$") || key.matches("^.*\\+\\d+$")

            if !isRawCodeOnly then
              // Deduplicate whichkey and screenshot internal aliases
              val isWhichKey = action.contains("polyomino-whichkey")
              val isScreenshotFull = action.contains("polyomino screenshot full") || action.contains("polyomino-screenshot full")

              val shouldAdd = if isWhichKey then
                if seenActions.contains("whichkey") then false else { seenActions += "whichkey"; true }
              else if isScreenshotFull then
                if seenActions.contains("screenshot_full") then false else { seenActions += "screenshot_full"; true }
              else
                true

              if shouldAdd then
                val displayKey = if isWhichKey then "Mod4+? / Mod4+/" else key
                val displayAction = friendlyAction(action)
                bindings = bindings :+ f"$displayKey%-24s → $displayAction"

    bindings

  private def friendlyAction(action: String): String =
    action match
      case a if a.contains("polyomino-sokoban") || a.contains("polyomino sokoban") => "Sokoban (Workspace & scratchpad arranger)"
      case a if a.contains("polyomino-2048") || a.contains("polyomino 2048") => "2048 (Memory & cache compactor)"
      case a if a.contains("polyomino-sweeper") || a.contains("polyomino sweeper") => "Minesweeper (Disk cache & orphan cleaner)"
      case a if a.contains("polyomino-lightsout") || a.contains("polyomino lightsout") => "Lights Out (Hardware & settings matrix)"
      case a if a.contains("polyomino-nonogram") || a.contains("polyomino nonogram") => "Nonogram (Theme & palette synthesizer)"
      case a if a.contains("sway-draw-window") || a.contains("draw-window") => "Draw interactive floating window (slurp)"
      case a if a.contains("floating-term") => "Floating terminal"
      case a if a.contains("sway-project-launcher") => "Project picker launcher (Neovim)"
      case a if a.contains("polyomino-menu") || a.contains("polyomino menu") => "Polyomino launcher menu"
      case a if a.contains("polyomino-whichkey") => "Keybindings cheatsheet"
      case a if a.contains("polyomino-theme-picker") => "Theme and wallpaper picker"
      case a if a.contains("wallpaper next") || a.contains("wallpaper cycle") => "Next wallpaper (active theme)"
      case a if a.contains("wallpaper prev") || a.contains("wallpaper previous") => "Previous wallpaper (active theme)"
      case a if a.contains("wallpaper random") => "Random wallpaper (active theme)"
      case a if a.contains("polyomino-wallpaper") || a.contains("wallpaper-picker") || a.contains("polyomino wallpaper") => "Wallpaper picker (active theme)"
      case a if a.contains("preview-lock") => "Preview lockscreen (Safe test window)"
      case a if a.contains("rubik-lock") || a.contains("polyomino-lock") || a.contains("lock") => "Lock screen (3D Rubik's Cube Lock)"
      case a if a.contains("power-menu") || a.contains("powermenu") => "Power menu (Session & Power Control)"
      case a if a.contains("systemctl suspend") => "Suspend system"
      case a if a.contains("systemctl poweroff") => "Shutdown system"
      case a if a.contains("systemctl reboot") => "Reboot system"
      case a if a.contains("polyomino screenshot full") => "Screenshot full screen"
      case a if a.contains("polyomino screenshot region") => "Screenshot region selection"
      case a if a.contains("polyomino screenshot window") => "Screenshot active window"
      case a if a.contains("record region") => "Record screen region (wf-recorder)"
      case a if a.contains("record window") => "Record active window (wf-recorder)"
      case a if a.contains("record-panel") => "Screen recording control panel"
      case a if a.contains("polyomino record") || a.contains("polyomino-record") => "Toggle screen recording (wf-recorder)"
      case a if a.contains("swaync-client -t -sw") => "Toggle notification center"
      case a if a.contains("kitty -e yazi") => "File manager (yazi)"
      case a if a.contains("kitty -e spotify_player") => "Spotify player TUI"
      case a if a.contains("kitty -e bluetui") => "Bluetooth manager (bluetui)"
      case a if a.contains("kitty -e impala") => "Network manager (impala)"
      case a if a.contains("kitty -e ncpamixer") => "Audio mixer (ncpamixer)"
      case a if a.contains("kitty -e aerc") => "Email client (aerc)"
      case a if a.contains("google-chrome-stable") => "Google Chrome"
      case a if a.contains("wdisplays") => "Display configuration (wdisplays)"
      case a if a.contains("swaynag") => "Exit Sway dialog"
      case a if a.contains("wofi --show drun") => "Application launcher"
      case a if a == "exec kitty" => "Open Kitty terminal"
      case a if a == "kill" => "Close focused window"
      case a if a == "reload" => "Reload Sway config"
      case a if a == "fullscreen" => "Toggle fullscreen"
      case a if a == "floating toggle" => "Toggle floating mode"
      case a if a == "focus mode_toggle" => "Toggle focus tiling/floating"
      case a if a == "focus parent" => "Focus parent container"
      case a if a == "splith" => "Split horizontal"
      case a if a == "splitv" => "Split vertical"
      case a if a == "layout toggle split" => "Toggle split layout"
      case a if a == "layout stacking" => "Layout stacking"
      case a if a == "layout tabbed" => "Layout tabbed"
      case a if a.startsWith("workspace number ") => s"Switch to Workspace ${a.stripPrefix("workspace number ")}"
      case a if a.startsWith("move container to workspace number ") => s"Move container to Workspace ${a.stripPrefix("move container to workspace number ")}"
      case a if a.startsWith("focus ") => s"Focus ${a.stripPrefix("focus ")}"
      case a if a.startsWith("move ") => s"Move ${a.stripPrefix("move ")}"
      case a if a == "move scratchpad" => "Move window to scratchpad"
      case a if a == "scratchpad show" => "Show scratchpad"
      case a if a == "mode \"resize\"" => "Enter resize mode"
      case other => other

  val defaultKeybindings: Seq[String] = Seq(
    "Mod4+Return              → Open Kitty terminal",
    "Mod4+d                   → Application launcher",
    "Mod4+Shift+q             → Close focused window",
    "Mod4+Shift+e             → Exit Sway dialog",
    "Mod4+f                   → Toggle fullscreen",
    "Mod4+v                   → Split vertical",
    "Mod4+b                   → Split horizontal",
    "Mod4+Shift+f             → File manager (yazi)",
    "Mod4+Shift+m             → Spotify (spotify_player)",
    "Mod4+Shift+u             → Bluetooth manager (bluetui)",
    "Mod4+Shift+v             → Audio mixer (ncpamixer)",
    "Mod4+Shift+a             → Email client (aerc)",
    "Mod4+Shift+n             → Toggle notification center",
    "Mod4+Shift+t             → Theme and wallpaper picker",
    "Mod4+Shift+p             → Wallpaper picker (active theme)",
    "Mod4+F6                  → Next wallpaper (active theme)",
    "Mod4+? / Mod4+/          → Keybindings cheatsheet",
    "Mod4+Escape              → Lock screen (3D Rubik's Cube Lock)",
    "Mod4+Mod1+s              → Sokoban (Workspace & scratchpad arranger)",
    "Mod4+Mod1+m              → 2048 (Memory & cache compactor)",
    "Mod4+Mod1+c              → Minesweeper (Disk cache & orphan cleaner)",
    "Mod4+Mod1+l              → Lights Out (Hardware & settings matrix)",
    "Mod4+Mod1+p              → Nonogram (Theme & palette synthesizer)",
    "Print                    → Screenshot full screen",
    "Mod4+Print               → Screenshot region selection",
    "Mod4+Shift+Print         → Screenshot active window"
  )

  /** Synchronous tile picker asking which capture target to record. Returns
    * "full" / "region" / "window", or None if the picker was cancelled. */
  def pickRecordTarget(ctx: Context): Option[String] =
    val tiles = Seq(
      tile("full", "󰍹", "Full Screen", "Record the entire output", "accent"),
      tile("region", "󰆞", "Region", "Draw a rectangle with slurp", "teal"),
      tile("window", "󰖯", "Window", "Record the focused window", "blue")
    )
    val selected = tilePick(ctx, "POLYOMINO // RECORD", tiles, columns = 3, width = 640, height = 280, badge = "RECORD")
    if selected.isEmpty then None else Some(selected)

  def runAudioPicker(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*AUDIO").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val (currentVol, isMuted) = try
      val res = os.proc("wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@").call(check = false).out.text().trim
      val muted = res.contains("[MUTED]")
      val pct = "([0-9]+\\.[0-9]+|[0-9]+)".r.findFirstIn(res).map(v => (v.toDouble * 100).round.toInt).getOrElse(50)
      (pct, muted)
    catch
      case _: Exception => (50, false)

    val tiles = Seq(
      tile("100", "󰕾", "100% Volume", "Maximum audio level", "accent", badge = if currentVol >= 95 && !isMuted then Some("CURRENT") else None),
      tile("80", "󰕾", "80% Volume", "High audio level", "teal", badge = if currentVol >= 75 && currentVol < 95 && !isMuted then Some("CURRENT") else None),
      tile("60", "󰖀", "60% Volume", "Comfortable indoor audio", "blue", badge = if currentVol >= 55 && currentVol < 75 && !isMuted then Some("CURRENT") else None),
      tile("40", "󰕿", "40% Volume", "Moderate audio level", "sky", badge = if currentVol >= 35 && currentVol < 55 && !isMuted then Some("CURRENT") else None),
      tile("20", "󰕿", "20% Volume", "Low night audio level", "subtext0", badge = if currentVol < 35 && !isMuted then Some("CURRENT") else None),
      tile("mute", "󰝟", "Toggle Output Mute", if isMuted then "Output is muted (click to unmute)" else "Mute speaker audio output", "red", badge = if isMuted then Some("MUTED") else None),
      tile("mic-mute", "󰍭", "Toggle Mic Mute", "Mute or unmute microphone input", "yellow"),
      tile("mixer", "󰓃", "Audio Mixer (Pavucontrol)", "Open graphical audio controller", "mauve")
    )

    val selected = tilePick(ctx, "POLYOMINO // AUDIO CONTROL", tiles, columns = 2, width = 640, height = 440, badge = "AUDIO")
    if selected.isEmpty then return Right(())

    selected match
      case "mute" =>
        try os.proc(ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-osd", "volume", "mute").call(check = false) catch case _: Exception => ()
      case "mic-mute" =>
        try os.proc(ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-osd", "volume", "mic-mute").call(check = false) catch case _: Exception => ()
      case "mixer" =>
        try
          if os.proc("which", "pavucontrol").call(check = false).exitCode == 0 then
            os.proc("pavucontrol").spawn(stdout = os.Inherit, stderr = os.Inherit)
          else
            os.proc("kitty", "--class=floating-term", "-T", "ncpamixer", "-e", "ncpamixer").spawn(stdout = os.Inherit, stderr = os.Inherit)
        catch case _: Exception => ()
      case vol if vol.forall(_.isDigit) =>
        try
          os.proc("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", s"${vol}%").call(check = false)
          os.proc(ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-osd", "volume", "raise", "0%").call(check = false)
        catch case _: Exception => ()
      case _ => ()

    Right(())

  def runBrightnessPicker(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*BRIGHTNESS").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val currentPct = try
      val res = os.proc("brightnessctl", "-m").call(check = false).out.text().trim
      val parts = res.split(",")
      if parts.length >= 4 then parts(3).stripSuffix("%").toIntOption.getOrElse(75) else 75
    catch
      case _: Exception => 75

    val tiles = Seq(
      tile("100", "󰃠", "100% Brightness", "Full daylight maximum backlight", "yellow", badge = if currentPct >= 95 then Some("CURRENT") else None),
      tile("80", "󰃠", "80% Brightness", "High brightness level", "accent", badge = if currentPct >= 75 && currentPct < 95 then Some("CURRENT") else None),
      tile("60", "󰃟", "60% Brightness", "Balanced indoor backlight", "teal", badge = if currentPct >= 55 && currentPct < 75 then Some("CURRENT") else None),
      tile("40", "󰃞", "40% Brightness", "Relaxed eye-comfort backlight", "blue", badge = if currentPct >= 35 && currentPct < 55 then Some("CURRENT") else None),
      tile("20", "󰃝", "20% Brightness", "Dim night workspace backlight", "sky", badge = if currentPct >= 15 && currentPct < 35 then Some("CURRENT") else None),
      tile("10", "󰃝", "10% Brightness", "Minimum screen backlight", "subtext0", badge = if currentPct < 15 then Some("CURRENT") else None),
      tile("nightlight", "󰖔", "Toggle Night Light", "Warm color temperature for eye comfort", "peach")
    )

    val selected = tilePick(ctx, "POLYOMINO // BRIGHTNESS CONTROL", tiles, columns = 2, width = 640, height = 400, badge = "BRIGHTNESS")
    if selected.isEmpty then return Right(())

    selected match
      case "nightlight" =>
        try
          val check = os.proc("pgrep", "-x", "wlsunset").call(check = false)
          if check.exitCode == 0 then
            os.proc("pkill", "-x", "wlsunset").call(check = false)
          else
            os.proc("wlsunset", "-t", "4000", "-T", "6500").spawn(stdout = os.Inherit, stderr = os.Inherit)
        catch case _: Exception => ()
      case pct if pct.forall(_.isDigit) =>
        try
          os.proc("brightnessctl", "set", s"${pct}%").call(check = false)
          os.proc(ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-osd", "brightness", "up", "0%").call(check = false)
        catch case _: Exception => ()
      case _ => ()

    Right(())

