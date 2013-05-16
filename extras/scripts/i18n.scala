import io.Source
import java.io._
import java.nio.charset.Charset

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
                           |~~ scala i18n.scala replace <absolutePathToReplaceFile> ~~~~> replaces all key-value pairs from a source properties-style file
                           |~~ scala i18n.scala renameKeys <absolutePathToMessagesFile> <absolutePathToReplaceFile> ~~~~> replaces all keys in a messages file given a source properties-style file
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

val usages: Seq[MessageUsage] = collectFiles(new File("../..")).flatMap { file =>
  Source.fromFile(file, "utf-8").getLines().zipWithIndex.flatMap { line =>
    Patterns.patterns.flatMap { p =>
      p.findAllIn(line._1).matchData.map { m =>
        MessageUsage(m.group(1), file, line._2 + 1, m.matched)
      }
    }
  }
}

def messageFilter(u: MessageUsage) =
  if (args.length > 0 && args(0) == "all" || args(0) == "unused" || args(0) == "replace") true
  else if (args.length > 0) u.key == args(0)
  else false

def fetchMessageKeys(messagesFile: File) = {
  val messages = Source.fromFile(messagesFile, "utf-8").getLines().filter { line => line.indexOf("=") > 0 }.toSeq
  messages.map { l => l.split("=")(0) }
}

def fetchReplacements(replaceFile: File) = {
  Source.fromFile(replaceFile, "utf-8").getLines().filter { line =>
    line.indexOf("=") > 0
  }.toSeq.map {line =>
    val Array(key, newKey) = line.split("=")
    (key, newKey)
  }
}

val matches = usages.filter(messageFilter)

if (args.length > 0 && args(0) == "unused") {

  val messagesFile = new File(args(1))
  if (!messagesFile.exists()) {
    println("Can't find messages file " + messagesFile)
  } else {
    val keys = fetchMessageKeys(messagesFile)
    val unusedKeys = keys.filterNot(key => matches.exists(_.key == key))
    println(Colors.blue("Unused keys for messages file " + messagesFile.getAbsolutePath))
    println()
    println(unusedKeys.mkString("\n"))
  }

} else if (args.length > 0 && args(0) == "replaceKeys") {

  val messagesFile = new File(args(1))
  if (!messagesFile.exists()) {
    println("Can't find messages file " + messagesFile)
  } else {
    val replacementFile = new File(args(2))
    if (!replacementFile.exists()) {
      println("Can't find replacement file " + replacementFile)
    } else {
      val replacements = fetchReplacements(replacementFile)
      val messages = fetchMessageKeys(messagesFile)

      replacements.foreach { pair =>
        Util.replace(messagesFile, pair._1, pair._2, false)
      }
    }
  }

} else if (args.length > 0 && args(0) == "replace") {

  val replaceFile = new File(args(1))
  if (!replaceFile.exists()) {
    println("Can't find replace file " + replaceFile)
  } else {
    val replacements: Seq[(String, String)] = fetchReplacements(replaceFile)

    replacements.foreach { r =>
      matches.find(_.key == r._1) foreach { m =>
        m.replace(r._2)
      }
    }


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

case class MessageUsage(key: String, file: File, line: Int, matched: String) {

  def replace(newKey: String) {
    Util.replace(file, key, newKey)
  }

}

object Patterns {

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

  val SCALA_MESSAGE_PATTERN_ALT = """\b&\b\{"([^"]+)"([^)])*\}""".r

  // label="foo.bar"
  val VIEW_DEFINITION_MESSAGE_PATTERN = """\blabel="([^"]+)"""".r


  // titleKey = "foo.bar"
  val TITLE_KEY_PATTERN = """\btitleKey = "([^"]+)"""".r

  val patterns = Seq(HTML_TAG_PATTERN, HTML_SQUOTE_MESSAGE_PATTERN, HTML_DQUOTE_MESSAGE_PATTERN, SCALA_MESSAGE_PATTERN, VIEW_DEFINITION_MESSAGE_PATTERN, TITLE_KEY_PATTERN, SCALA_MESSAGE_PATTERN_ALT)

}

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

object Util {

  def replace(file: File, key: String, newKey: String, replaceWithinQuotes: Boolean = true) {
    val source = Source.fromFile(file, "utf-8").getLines().mkString("\n")
    val replacement = if (replaceWithinQuotes) {
      val singleQuotePattern = "'%s'".format(key).r
      val doubleQuotePattern = """"%s"""".format(key).r
      val first = singleQuotePattern.replaceAllIn(source, "'" + newKey + "'")
      doubleQuotePattern.replaceAllIn(first, "\"" + newKey + "\"")
    } else {
      key.r.replaceAllIn(source, newKey)
    }

    println(Colors.blue("Replaced key '%s' with new key '%s' in file %s".format(key, newKey, file.getAbsolutePath )))

    Some(new PrintWriter(file.getAbsolutePath, "utf-8")).foreach{p => p.write(replacement); p.close() }
  }

}