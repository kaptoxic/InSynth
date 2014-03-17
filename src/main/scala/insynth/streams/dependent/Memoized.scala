package insynth.streams
package dependent

import scala.collection.mutable

import light._

trait Memoized[I, O] extends Dependent[I, O] with Memoizable {

  val memoizedMap = mutable.Map[I, Enum[O]]()
  
  override abstract def getStream(parameter: I) = {
    memoizedMap.getOrElseUpdate(parameter, super.getStream(parameter))
  }
  
  override def clearMemoization = memoizedMap.clear
  
}

// should return all finite/infinite enumerables

//trait FiniteDependent[I, +O] extends Dependent[I, O] {
//
//  override def getStream(parameter: I): Finite[O]
//  
//}
//
//trait InfiniteDependent[I, +O] extends Dependent[I, O] {
//
//  override def getStream(parameter: I): Infinite[O]
//  
//}