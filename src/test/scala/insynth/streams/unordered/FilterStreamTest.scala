package insynth
package streams
package unordered

import org.scalatest._
import org.scalatest.matchers._

import reconstruction.stream.Application
import reconstruction.stream._

import util._
import common._
import util.format._

class FilterStreamTest extends FunSuite with ShouldMatchers {

  import Utils._
  
  test("simple filtering test") {
    val stream = FilterStream(
      SingleStream(Stream(1, 2, 3), false), { (x: Int) => x % 2 == 0 }
    )
    
    stream.getStream.toList should be (
      Vector(2)
    )
    
    compareCallsToGetStream( stream )
  }
  
  ignore("infinite values test") {
  test("infinite values test") {
    val stream = FilterStream(
      SingleStream(Stream(1, 2, 3), false), { (x: Int) => x % 2 == 0 }
    )
    
    stream.getStream.toList should be (
      Vector(2)
    )
    
    compareCallsToGetStream( stream )
  }
  }

  
}