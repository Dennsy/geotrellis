package geotrellis.spark.etl

import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.raster.Tile
import geotrellis.spark.util.SparkUtils
import geotrellis.vector.ProjectedExtent
import org.apache.spark.SparkConf

object SinglebandIngest {
  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("GeoTrellis ETL SinglebandIngest", new SparkConf(true))
    try {
      Etl.ingest[ProjectedExtent, SpatialKey, Tile](args, ZCurveKeyIndexMethod)
    } finally {
      sc.stop()
    }
  }
}
