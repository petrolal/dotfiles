package polyomino.dotfiles.sysutils

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

import scala.util.control.NonFatal

/** `record-panel` -- small floating single-line status bar shown while a
  * `wf-recorder` capture started via `polyomino record` is active. Spawned
  * automatically by SysUtils.spawnRecordPanel in its own kitty window; not
  * meant to be launched standalone (though it degrades gracefully if run
  * without an active recording). */
object RecordPanel:

  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    if args.contains("--help") || args.contains("-h") then
      println(HelpText)
      Right(())
    else
      val fileArg = args.find(a => !a.startsWith("-"))
      if args.contains("--dry-run") || args.contains("--print") then
        Console.out.print(Renderer.frame(0.0, recording = true, paused = false, fileArg))
        Console.out.println("\u001b[0m")
        Right(())
      else if ctx.isTest then
        Right(())
      else
        try
          interactive(fileArg)
          Right(())
        catch
          case NonFatal(e) =>
            restoreTerminal()
            Left(CommandError(s"record-panel failed: ${e.getMessage}"))
        finally restoreTerminal()

  private enum Outcome:
    case Save, Discard, ExternalStop

  private def interactive(fileArg: Option[String]): Unit =
    setupTerminal()
    val startTime = System.nanoTime()
    var paused = false
    var outcome: Option[Outcome] = None

    while outcome.isEmpty do
      if !isRecording() then
        outcome = Some(Outcome.ExternalStop)
      else
        for b <- readAvailable() do
          b.toChar match
            case 's' | 'S' | '\r' | '\n' | ' ' => outcome = Some(Outcome.Save)
            case 'p' | 'P' =>
              try os.proc("pkill", "-USR1", "-x", "wf-recorder").call(check = false)
              catch case NonFatal(_) => ()
              paused = !paused
            case 'c' | 'C' | 'q' | 'Q' | 27 => outcome = Some(Outcome.Discard)
            case _ => ()

        if outcome.isEmpty then
          val elapsed = (System.nanoTime() - startTime) / 1e9
          Console.out.print(Renderer.frame(elapsed, recording = true, paused = paused, fileArg))
          Console.out.flush()
          Thread.sleep(200)

    restoreTerminal()
    outcome.get match
      case Outcome.Save =>
        try os.proc("pkill", "-INT", "-x", "wf-recorder").call(check = false)
        catch case NonFatal(_) => ()
        println("\u001b[1;34m[polyomino record-panel]\u001b[0m Stopped and saved recording.")
      case Outcome.Discard =>
        try os.proc("pkill", "-INT", "-x", "wf-recorder").call(check = false)
        catch case NonFatal(_) => ()
        Thread.sleep(300)
        fileArg.foreach { f =>
          try
            val p = os.Path(f, os.pwd)
            if os.exists(p) then os.remove(p)
          catch case NonFatal(_) => ()
        }
        println("\u001b[1;33m[polyomino record-panel]\u001b[0m Recording discarded.")
      case Outcome.ExternalStop =>
        println("\u001b[1;34m[polyomino record-panel]\u001b[0m Recording ended.")

  private def isRecording(): Boolean =
    try os.proc("pgrep", "-x", "wf-recorder").call(check = false).exitCode == 0
    catch case _: Exception => false

  // -- terminal plumbing (mirrors PowerMenu's) --------------------------------

  @volatile private var savedStty: Option[String] = None
  @volatile private var restored = true
  @volatile private var shutdownHook: Option[Thread] = None

  private def setupTerminal(): Unit =
    savedStty =
      try Some(os.proc("stty", "-g").call(stdin = os.Inherit, stderr = os.Pipe).out.trim())
      catch case NonFatal(_) => None
    try os.proc("stty", "-echo", "-icanon", "min", "0", "time", "0").call(stdin = os.Inherit, stderr = os.Pipe)
    catch case NonFatal(_) => ()
    restored = false
    val hook = new Thread(() => restoreTerminal())
    shutdownHook = Some(hook)
    Runtime.getRuntime.addShutdownHook(hook)
    grabFocus()
    Console.out.print("\u001b[?1049h\u001b[?25l\u001b[2J")
    Console.out.flush()

  private def grabFocus(): Unit =
    var i = 0
    while i < 4 do
      try os.proc("swaymsg", "[app_id=polyomino-record-panel]", "focus").call(check = false, stderr = os.Pipe)
      catch case NonFatal(_) => ()
      i += 1
      if i < 4 then try Thread.sleep(60) catch case NonFatal(_) => ()
    try os.proc("swaymsg", "[app_id=polyomino-record-panel]", "opacity", "0.94").call(check = false, stderr = os.Pipe)
    catch case NonFatal(_) => ()

  private def restoreTerminal(): Unit = synchronized {
    if !restored then
      restored = true
      shutdownHook.foreach { h =>
        try Runtime.getRuntime.removeShutdownHook(h) catch case NonFatal(_) => ()
        shutdownHook = None
      }
      Console.out.print("\u001b[?25h\u001b[?1049l\u001b[0m")
      Console.out.flush()
      savedStty match
        case Some(s) => try os.proc("stty", s).call(stdin = os.Inherit, stderr = os.Pipe) catch case NonFatal(_) => ()
        case None    => try os.proc("stty", "sane").call(stdin = os.Inherit, stderr = os.Pipe) catch case NonFatal(_) => ()
  }

  private def readAvailable(): List[Int] =
    try
      val n = System.in.available()
      if n <= 0 then Nil
      else
        val b = new Array[Byte](math.min(n, 64))
        val r = System.in.read(b)
        if r <= 0 then Nil else b.take(r).map(_ & 0xff).toList
    catch case NonFatal(_) => Nil

  // -- rendering ---------------------------------------------------------------

  private object Renderer:
    def frame(elapsedSecs: Double, recording: Boolean, paused: Boolean, fileArg: Option[String]): String =
      val totalSecs = elapsedSecs.toInt
      val mm = totalSecs / 60
      val ss = totalSecs % 60
      val timer = f"$mm%02d:$ss%02d"
      val statusLabel = if paused then "PAUSED" else "RECORDING"
      val statusColor = if paused then "\u001b[1;33m" else "\u001b[1;31m"
      val fileLine = fileArg.map(f => os.Path(f, os.pwd).last).getOrElse("recording")

      val sb = new StringBuilder
      sb.append("\u001b[H\u001b[2J")
      sb.append(
        s" $statusColor\u25cf $statusLabel\u001b[0m \u001b[1m$timer\u001b[0m \u001b[2m$fileLine\u001b[0m" +
          s"  \u001b[32ms\u001b[0m stop \u001b[33mp\u001b[0m pause \u001b[31mc\u001b[0m cancel"
      )
      sb.toString

  private val HelpText: String =
    """polyomino record-panel -- floating control panel for an active wf-recorder capture.
      |
      |Normally spawned automatically by `polyomino record start`/`toggle` in its
      |own floating kitty window; can be run standalone for debugging.
      |
      |Controls:
      |  s / enter / space   stop recording and keep the file
      |  p                   pause/resume (sends SIGUSR1 to wf-recorder)
      |  c / q / esc         cancel recording and delete the output file
      |
      |Flags:
      |  --dry-run   render one frame to stdout and exit (no input, no action)
      |  --help      this text
      |""".stripMargin
