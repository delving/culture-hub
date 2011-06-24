import eu.europeana.sip.core.GroovyList
import eu.europeana.sip.core.GroovyNode
import eu.europeana.sip.core.DiscardRecordException

// MappingCategory is a class used as a Groovy Category to add methods to existing classes

public class MappingCategory {

  static void discard(Boolean condition, String why) {
    if (condition) throw new DiscardRecordException(why)
  }

  static GroovyList children(String string) {
    return new GroovyList(string);
  }

  static String getAt(GroovyNode node, Object what) {
    return node.toString()[what]
  }

  static GroovyList ifAbsentUse(GroovyList list, Object factVariable) {
    if (!list) {
      list += factVariable
    }
    else if (list.size() == 1) {
      GroovyNode node = (GroovyNode) list[0]
      if (!node.text()) {
        list += factVariable
      }
    }
    return list
  }

  static Object plus(a, b) { // operator +
    GroovyList both = new GroovyList()
    both.addAll(a.children())
    both.addAll(b.children())
    return both;
  }

  static Object or(a, b) { // operator |
    GroovyList listA = a.children()
    GroovyList listB = b.children()
    GroovyList tupleList = new GroovyList()
    int max = Math.min(listA.size(), listB.size());
    for (Integer index : 0..(max-1) ) tupleList.add(new GroovyList(listA[index], listB[index]))
    return tupleList
  }

  static Object multiply(a, Closure closure) { // operator *
    for (Object child: a.children()) closure.call(child);
    return null
  }

  static GroovyList multiply(a, String delimiter) {
    Iterator walk = a.children().iterator();
    StringBuilder out = new StringBuilder()
    while (walk.hasNext()) {
      out.append(walk.next())
      if (walk.hasNext()) {
        out.append(delimiter)
      }
    }
    return new GroovyList(out.toString())
  }

  static Object power(a, Closure closure) {  // operator **
    for (Object child: a.children()) {
      closure.call(child)
      break
    }
    return null
  }

  static GroovyList mod(a, String regex) {
    GroovyList all = new GroovyList();
    for (Object node: a.children()) {
      if (node instanceof GroovyNode) {
        all += new GroovyList(node.text().split(regex))
      }
    }
    return all;
  }

  static GroovyList extractYear(GroovyList target) {
    return extractYear(target.text())
  }

  static GroovyList extractYear(GroovyNode target) {
    return extractYear(target.text())
  }

  static GroovyList extractYear(String text) {
    GroovyList result = new GroovyList()
    switch (text) {

      case ~/$normalYear/:
        result += (text =~ /$year/)[0]
        break

      case ~/$yearAD/:
        result += (text =~ /$yr/)[0] + ' AD'
        break

      case ~/$yearBC/:
        result += (text =~ /$yr/)[0] + ' BC'
        break

      case ~/$yearRange/:
        def list = text =~ /$year/
        if (list[0] == list[1]) {
          result += list[0]
        }
        else {
          result += list[0]
          result += list[1]
        }
        break

      case ~/$yearRangeBrief/:
        def list = text =~ /\d{1,4}/
        result += list[0]
        result += list[0][0] + list[0][1] + list[1]
        break

      case ~/$yr/:
        result += text + ' AD'
        break

      default:
        text.eachMatch(/$year/) {
          result += it
        }
        break
    }
    return result
  }

  static GroovyList toId(GroovyNode identifier, spec) {
    return new GroovyList(toId(identifier.text(), spec))
  }

  static GroovyList toId(GroovyList identifier, spec) {
    return new GroovyList(toId(identifier.toString(), spec))
  }

  static String toId(String identifier, spec) {
    if (!spec) {
      throw new MissingPropertyException("spec", String.class)
    }
    if (!identifier) {
      throw new MissingPropertyException("Identifier passed to toId", String.class)
    }
    def uriBytes = identifier.toString().getBytes("UTF-8");
    def digest = java.security.MessageDigest.getInstance("SHA-1")
    def hash = new StringBuilder()
    for (Byte b in digest.digest(uriBytes)) {
      hash.append('0123456789ABCDEF'[(b & 0xF0) >> 4])
      hash.append('0123456789ABCDEF'[b & 0x0F])
    }
    return "$spec/$hash"
  }

  static String sanitize(GroovyNode node) {
    return sanitize(node.toString())
  }

  static String sanitize(GroovyList list) {
    return sanitize(list.toString())
  }

  static String sanitize(String text) { // same effect as in eu.delving.metadata.Sanitizer.sanitizeGroovy, except apostrophe removal
    text = (text =~ /\n/).replaceAll(' ')
    text = (text =~ / +/).replaceAll(' ')
    return text
  }

  static year = /\d{4}/
  static dateSlashA = /$year\/\d\d\/\d\d\//
  static dateDashA = /$year-\d\d-\d\d/
  static dateSlashB = /\d\d\/\d\d\/$year/
  static dateDashB = /\d\d-\d\d-$year/
  static ad = /(ad|AD|a\.d\.|A\.D\.)/
  static bc = /(bc|BC|b\.c\.|B\.C\.)/
  static yr = /\d{1,3}/
  static yearAD = /$yr\s?$ad/
  static yearBC = /$yr\s?$bc/
  static normalYear = /($year|$dateSlashA|$dateSlashB|$dateDashA|$dateDashB)/
  static yearRangeDash = /$normalYear-$normalYear/
  static yearRangeTo = /$normalYear to $normalYear/
  static yearRange = /($yearRangeDash|$yearRangeTo)/
  static yearRangeBrief = /$year-\d\d/
}