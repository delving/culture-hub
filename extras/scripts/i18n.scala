import io.Source
import java.io._

println(Colors.green("i18n cruncher"))
println(Colors.green("============="))
println()
if (args.isEmpty) {
  println(Colors.magenta("""
                           |Usage:
                           |
                           |~~ scala i18n.scala all ~~~~> prints all usages
                           |~~ scala i18n.scala "ui.label.name" ~~~~> prints usages for one key
                           |~~ scala i18n.scala unused <absolutePathToMessagesFile> ~~~~> prints which keys are not used for a given messages file
                           |""".stripMargin))
} else {


def collectFiles(dir: File): Array[File] = {
  val files = dir.listFiles()
  if (files != null) {
    files.
      filter(f =>
        f.getName.endsWith(".scala") ||
        f.getName.endsWith(".html") ||
        f.getName.endsWith("-view-definition.xml") ||
        f.isDirectory).
      filterNot(_.getName == "target").
      flatMap(f => if (f.isDirectory) collectFiles(f) else Array(f))
  } else {
    Array[File]()
  }
}

// &{'foo.bar'}
// &{'foo.bar', baz}
val HTML_TAG_PATTERN = """&\{'([^']+)'([^)])*}""".r

// messages.get('foo.bar')
// messages.get('foo.bar', baz)
// messages.get("foo.bar")
// messages.get("foo.bar", baz)
val HTML_SQUOTE_MESSAGE_PATTERN = """\bmessages\.get\b\('([^']+)'([^)])*\)""".r
val HTML_DQUOTE_MESSAGE_PATTERN = """\bmessages\.get\b\("([^"]+)"([^)])*\)""".r

// Messages("foo.bar")
// Messages("foo.bar", "bla")
val SCALA_MESSAGE_PATTERN = """\bMessages\b\("([^"]+)"([^)])*\)""".r

// label="foo.bar"
val VIEW_DEFINITION_MESSAGE_PATTERN = """\blabel="([^"]+)"""".r

val patterns = Seq(HTML_TAG_PATTERN, HTML_SQUOTE_MESSAGE_PATTERN, HTML_DQUOTE_MESSAGE_PATTERN, SCALA_MESSAGE_PATTERN, VIEW_DEFINITION_MESSAGE_PATTERN)

val usages: Seq[MessageUsage] = collectFiles(new File("../..")).flatMap { file =>
  Source.fromFile(file, "utf-8").getLines().zipWithIndex.flatMap { line =>
    patterns.flatMap { p =>
      p.findAllIn(line._1).matchData.map { m =>
        MessageUsage(m.group(1), file, line._2 + 1, m.matched)
      }
    }
  }
}

def messageFilter(u: MessageUsage) =
  if (args.length > 0 && args(0) == "all" || args(0) == "unused") true
  else if (args.length > 0) u.key == args(0)
  else false


val matches = usages.filter(messageFilter)

if (args.length > 0 && args(0) == "unused") {

  val messagesFile = new File(args(1))
  if (!messagesFile.exists()) {
    println("Can't find messages file " + messagesFile)
  } else {
    val messages = Source.fromFile(messagesFile, "utf-8").getLines().filter { line => line.indexOf("=") > 0 }.toSeq
    val keys = messages.map { l => l.split("=")(0) }
    println(matches.map(_.key).mkString("\n"))
    val unusedKeys = keys.filterNot(key => matches.exists(_.key == key))
    println(Colors.blue("Unused keys for messages file " + messagesFile.getAbsolutePath))
    println()
    println(unusedKeys.mkString("\n"))
  }

} else {
  matches.groupBy(_.key).foreach { group =>
    println(Colors.blue(group._1))
    println()
    group._2.groupBy(_.file).foreach { usage =>
      println("  In " + Colors.yellow(usage._1.getPath.substring(2)))
      println()
      usage._2.foreach { u =>
        println("    " + Colors.red(u.line.toString) + "\t" + u.matched)
        println()
      }
    }
    println()
  }

  println("Found %s %s".format(matches.length, if (matches.length == 1) "match" else "matches"))
  println()
}

}

case class MessageUsage(key: String, file: File, line: Int, matched: String)



// shamelessly stolen from Play - https://github.com/playframework/Play20/blob/master/framework/src/console/src/main/scala/Console.scala
object Colors {

  import scala.Console._

  lazy val isANSISupported = {
    Option(System.getProperty("sbt.log.noformat")).map(_ != "true").orElse {
      Option(System.getProperty("os.name"))
        .map(_.toLowerCase)
        .filter(_.contains("windows"))
        .map(_ => false)
    }.getOrElse(true)
  }

  def red(str: String): String = if (isANSISupported) (RED + str + RESET) else str
  def blue(str: String): String = if (isANSISupported) (BLUE + str + RESET) else str
  def cyan(str: String): String = if (isANSISupported) (CYAN + str + RESET) else str
  def green(str: String): String = if (isANSISupported) (GREEN + str + RESET) else str
  def magenta(str: String): String = if (isANSISupported) (MAGENTA + str + RESET) else str
  def white(str: String): String = if (isANSISupported) (WHITE + str + RESET) else str
  def black(str: String): String = if (isANSISupported) (BLACK + str + RESET) else str
  def yellow(str: String): String = if (isANSISupported) (YELLOW + str + RESET) else str

}