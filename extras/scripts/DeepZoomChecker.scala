import collection.immutable.Seq
import java.io.File
import java.util.zip.GZIPInputStream
import xml.{NodeSeq, XML, Source}

// Get variables from the command prompt

// Assign the link to the Sip-Creator DataSet Directory
val dataSetPath = if (args.size>0) args(0) else "/Users/kiivihal/DelvingSIPCreator/brabantcloud_eu__80___molspof/museum-voor-religieuze-kunst_brabantcloud/"
val recDef = if (args.size>1) args(1) else "tib"
val freq = if (args.size>2) args(2) else ""
val numb = if (args.size>3) args(3) else ""

// get dataset name
val dataSetDir: File = new File(dataSetPath)
val dataSetName = dataSetDir.getParentFile.getName

// read and parse the statistics file *_stastistics.gz
val resultStatisticsFile =
  dataSetDir.listFiles().filter(_.getName.endsWith(s"stats-result_${recDef}.xml.gz")).sortBy(f => f.lastModified() > f.lastModified()).headOption

if (resultStatisticsFile == None) {
  println(s"Unable to find the result statistics file. Please make sure you have validated ${recDef} for DataSet ${dataSetName}.")
  System.exit(1)
}

val statisticsFile  = XML.load(new GZIPInputStream(Source.fromFile(resultStatisticsFile.get.getAbsolutePath).getByteStream))

// parse output result5 DeepZoom
val deepZoom: NodeSeq = (statisticsFile \\ "field-value-stats" \ "entry").filter(p => (p \\ "path").text.endsWith("delving:deepZoomUrl"))

// Create list of all entries
val deepZoomUriList: Seq[String] = (deepZoom  \\ "value-stats" \ "values" \ "entry" \ "string").map(_.text)

// define regex Extractor      for http://media.delving.org/iip/deepzoom/mnt/tib/tiles/brabantcloud/mrk/0028.tif.dzi
val DeepZoomExtrator =  "http://(.*?)/iip/deepzoom(.*)/(.*?).tif.dzi".r

// extract the route on media server
val DeepZoomExtrator(host, route, id) = deepZoomUriList.head

// log into media server and get list of tiles in the route dir
val tiffList: Iterable[String] = jassh.SSH.once(host, "sjoerd") {_ executeAndTrimSplit "ls -lha %s | awk {'print $9'}".format(route)}

// modify list to mirror how deepZoom Urls look

val DeepZoomFileUrls = tiffList.map{tiff => s"http://${host}/iip/deepzoom${route}/${tiff}.dzi"}

println(DeepZoomFileUrls)
// output general statistics and write lists to txt files (in subdir of dataset name)

// nr of items in sip-creator list
// nr of items on disk
// nr of items not on disk
// nr of items not in sip-creator
// nr of differences due to case differences