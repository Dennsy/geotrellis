package geotrellis.spark.io.cassandra

import geotrellis.raster.Tile
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.testfiles.TestFiles

class CassandraSpaceTimeSpec
  extends PersistenceSpec[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]]
    with SpaceTimeKeyIndexMethods
    with TestEnvironment
    with CassandraTestEnvironment
    with TestFiles
    with CoordinateSpaceTimeSpec
    with LayerUpdateSpaceTimeTileSpec {

  lazy val instance       = BaseCassandraInstance(Seq("127.0.0.1"))
  lazy val attributeStore = CassandraAttributeStore(instance)

  lazy val reader    = CassandraLayerReader(attributeStore)
  lazy val writer    = CassandraLayerWriter(attributeStore, "geotrellis", "tiles")
  lazy val deleter   = CassandraLayerDeleter(attributeStore)
  lazy val updater   = CassandraLayerUpdater(attributeStore)
  lazy val tiles     = CassandraValueReader(attributeStore)
  lazy val sample    = CoordinateSpaceTime
  lazy val copier    = CassandraLayerCopier(attributeStore, reader, writer)
  lazy val reindexer = CassandraLayerReindexer(attributeStore, reader, writer, deleter, copier)
  lazy val mover     = CassandraLayerMover(copier, deleter)
}
