// RecordAnalysis.groovy - the place for helpful methods
//
//        Average number of fields per record
//        max fields per record
//        min fields per record
//        average words per field
//        average words per record
//        average words per field per record
//        total number of records, fields, words
//        ??? total number of fields with all unique values.
//        total number of fields with an url in it  (contains http://??? or https://???)

class RecordAnalysis {
  int maxNodes;
  int recordCount;

  def consumeRecord(input) {
    recordCount++;
    int fieldCount = input._.size();
    if (fieldCount > maxNodes) {
      maxNodes = fieldCount;
    }
  }

  def produceHtml(output) {
    output.html {
      body {
        h1 "record count $recordCount"
        ul {
          li "max fields $maxNodes"
        }
      }
    }
  }
}

