package geotrellis.proj4

import geotrellis.proj4.io.wkt.WKT
import org.osgeo.proj4j._

import scala.io.Source

object CRS {
  private lazy val proj4ToEPSGMap = new Memoize[String, Option[String]](readEPSGCodeFromFile)
  private val crsFactory = new CRSFactory
  private val filePrefix = "/geotrellis/proj4/nad/"

  /**
    * Creates a CoordinateReferenceSystem
    * from a PROJ.4 projection parameter string.
    * <p>
    * An example of a valid PROJ.4 projection parameter string is:
    * <pre>
    * +proj=aea +lat_1=50 +lat_2=58.5 +lat_0=45 +lon_0=-126 +x_0=1000000 +y_0=0 +ellps=GRS80 +units=m
    * </pre>
    *
    * @param  proj4Params  A PROJ.4 projection parameter string
    * @return              The specified CoordinateReferenceSystem
    */
  def fromString(proj4Params: String): CRS =
    new CRS {
      val proj4jCrs = crsFactory.createFromParameters(null, proj4Params)

      def epsgCode: Option[Int] = getEPSGCode(toProj4String + " <>")
    }

  /**
    * Returns the numeric EPSG code of a proj4string.
    */
  def getEPSGCode(proj4String: String): Option[Int] =
    proj4ToEPSGMap(proj4String).map(_.toInt)

  /**
    * Creates a CoordinateReferenceSystem
    * from a PROJ.4 projection parameter string.
    * <p>
    * An example of a valid PROJ.4 projection parameter string is:
    * <pre>
    * +proj=aea +lat_1=50 +lat_2=58.5 +lat_0=45 +lon_0=-126 +x_0=1000000 +y_0=0 +ellps=GRS80 +units=m
    * </pre>
    *
    * @param  name         A name for this coordinate system.
    * @param  proj4Params  A PROJ.4 projection parameter string
    * @return              The specified CoordinateReferenceSystem
    */
  def fromString(name: String, proj4Params: String): CRS =
    new CRS {
      val proj4jCrs = crsFactory.createFromParameters(name, proj4Params)

      def epsgCode: Option[Int] = getEPSGCode(toProj4String + " <>")
    }

  /**
    * Creates a CoordinateReferenceSystem (CRS) from a
    * well-known-text String.
    */
  def fromWKT(wktString: String): CRS = {
    val epsgCode: String = WKT.getEPSGCode(wktString)

    fromName(epsgCode)
  }

  /**
    * Creates a CoordinateReferenceSystem (CRS) from a well-known name.
    * CRS names are of the form: "<tt>authority:code</tt>",
    * with the components being:
    * <ul>
    * <li><b><tt>authority</tt></b> is a code for a namespace supported by
    * PROJ.4.
    * Currently supported values are
    * <tt>EPSG</tt>,
    * <tt>ESRI</tt>,
    * <tt>WORLD</tt>,
    * <tt>NA83</tt>,
    * <tt>NAD27</tt>.
    * If no authority is provided, the <tt>EPSG</tt> namespace is assumed.
    * <li><b><tt>code</tt></b> is the id of a coordinate system in the authority namespace.
    * For example, in the <tt>EPSG</tt> namespace a code is an integer value
    * which identifies a CRS definition in the EPSG database.
    * (Codes are read and handled as strings).
    * </ul>
    * An example of a valid CRS name is <tt>EPSG:3005</tt>.
    * <p>
    *
    * @param   name  The name of a coordinate system, with optional authority prefix
    * @return        The CoordinateReferenceSystem corresponding to the given name
   */
  def fromName(name: String): CRS =
    new CRS {
      val proj4jCrs = crsFactory.createFromName(name)

      def epsgCode: Option[Int] = getEPSGCode(toProj4String + " <>")
    }

  /**
    * Creates a CoordinateReferenceSystem (CRS) from an EPSG code.
    */
  def fromEpsgCode(epsgCode: Int) =
    fromName(s"EPSG:$epsgCode")

  private def readEPSGCodeFromFile(proj4String: String): Option[String] = {
    val stream = getClass.getResourceAsStream(s"${filePrefix}epsg")
    try {
      Source.fromInputStream(stream)
        .getLines
        .find { line =>
          !line.startsWith("#") && {
            val proj4Body = line.split("proj")(1)
            s"+proj$proj4Body" == proj4String
          }
        }.flatMap { l =>
          val array = l.split(" ")
          val length = array(0).length
          Some(array(0).substring(1, length - 1))
        }
    } finally {
      stream.close()
    }
  }
}


trait CRS extends Serializable {

  val Epsilon = 1e-8

  def epsgCode: Option[Int]

  def proj4jCrs: CoordinateReferenceSystem

  /**
    * Override this function to handle reprojecting to another CRS in
    * a more performant way.
    */
  def alternateTransform(dest: CRS): Option[(Double, Double) => (Double, Double)] =
    None

  /**
   * Returns the WKT representation of the Coordinate Reference
   * System.
   */
  def toWKT(): Option[String] = epsgCode.map(WKT.fromEPSGCode(_))


  // TODO: Do these better once more things are ported
  override
  def hashCode = toProj4String.hashCode

  def toProj4String: String = proj4jCrs.getParameterString

  def isGeographic: Boolean = proj4jCrs.isGeographic

  override
  def equals(o: Any): Boolean =
    o match {
      case other: CRS => compareProj4Strings(other.toProj4String, toProj4String)
      case _ => false
    }

  private def compareProj4Strings(p1: String, p2: String) = {
    def toProj4Map(s: String): Map[String, String] =
      s.trim.split(" ").map(x =>
        if (x.startsWith("+")) x.substring(1) else x).map(x => {
        val index = x.indexOf('=')
        if (index != -1) (x.substring(0, index) -> Some(x.substring(index + 1)))
        else (x -> None)
      }).groupBy(_._1).map { case (a, b) => (a, b.head._2) }
        .filter { case (a, b) => !b.isEmpty }.map { case (a, b) => (a -> b.get) }
        .map { case (a, b) => if (b == "latlong") (a -> "longlat") else (a, b) }
        .filter { case (a, b) => (a != "to_meter" || b != "1.0") }

    val m1 = toProj4Map(p1)
    val m2 = toProj4Map(p2)

    m1.map {
      case (key, v1) => m2.get(key) match {
        case Some(v2) => compareValues(v1, v2)
        case None => false
      }
    }.forall(_ != false)
  }

  private def compareValues(s1: String, s2: String) = {
    def isNumber(s: String) = s.filter(c => !List('.', '-').contains(c)) forall Character.isDigit

    val s2IsNumber = isNumber(s1)
    val s1IsNumber = isNumber(s2)

    if (s1IsNumber == s2IsNumber) {
      if (s1IsNumber) math.abs(s1.toDouble - s2.toDouble) < Epsilon
      else s1 == s2
    } else false
  }

  protected def factory = CRS.crsFactory
}
