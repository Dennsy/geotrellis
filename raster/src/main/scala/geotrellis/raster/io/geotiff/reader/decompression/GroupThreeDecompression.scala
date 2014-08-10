/*
 * Copyright (c) 2014 Azavea.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.raster.io.geotiff.reader.decompression

import monocle.syntax._
import monocle.Macro._

import geotrellis.raster.io.geotiff.reader._
import geotrellis.raster.io.geotiff.reader.ImageDirectoryLenses._

import spire.syntax.cfor._

object GroupThreeDecompression {

  implicit class GroupThree(matrix: Array[Array[Byte]]) {
    def uncompressGroupThree(implicit directory: ImageDirectory): Array[Array[Byte]] = {
      val options = directory |-> t4OptionsLens get
      val fillOrder = directory |-> fillOrderLens get
      val len = matrix.length
      var arr = Array.ofDim[Array[Byte]](len)

      cfor(0)(_ < len, _ + 1) { i =>
        val segment = matrix(i)
        val length = directory.rowsInSegment(i)
        val width = directory.rowSize
        val decompressor = new TIFFFaxDecoder(fillOrder, width, length)

        val outputArray = Array.ofDim[Byte]((length * width + 7) / 8)

        decompressor.decode2D(outputArray, segment, 0, length, options)

        arr(i) = outputArray
      }

      arr
    }
  }

}
