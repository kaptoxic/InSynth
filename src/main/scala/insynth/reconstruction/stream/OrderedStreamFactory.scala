package insynth.reconstruction.stream

import insynth.streams._
import insynth.streams.ordered._

import insynth.util.logging.HasLogger

class OrderedStreamFactory[T] extends StreamFactory[T] with HasLogger {
  
  override def makeEmptyStreamable = Empty

  override def makeSingleton[U <: T](element: U) = Singleton(element)
  
  override def makeSingletonList[U <: T](element: List[U]) = Singleton(element)
  
//  override def makeSingleStream[U <: T](stream: => Stream[U], isInfiniteFlag: Boolean) =
//    SingleStream(stream, isInfiniteFlag)
  
  override def makeUnaryStream[X, Y <: T](streamable: Streamable[X], modify: X=>Y, modifyVal: Option[Int => Int] = None) =
    UnaryStream(streamable.asInstanceOf[OrderedStreamable[X]], modify, modifyVal)
  
  override def makeUnaryStreamList[X, Y <: T](streamable: Streamable[X], modify: X => List[Y]) =
    UnaryStream(streamable.asInstanceOf[OrderedStreamable[X]], modify)
  
  override def makeBinaryStreamToList[X, Y, Z <: T](s1: Streamable[X], s2: Streamable[Y])(combine: (X, Y) => List[Z]) =
    BinaryStream(s1.asInstanceOf[OrderedStreamable[X]], s2.asInstanceOf[OrderedStreamable[Y]])(combine)

  override def makeBinaryStream[X, Y <: T, Z <: T](s1: Streamable[X], s2: Streamable[List[Y]])
  	(combine: (X, List[Y]) => Z) = BinaryStream(s1.asInstanceOf[OrderedStreamable[X]], s2.asInstanceOf[OrderedStreamable[List[Y]]])(combine)
  
  override def makeRoundRobbin[U <: T](streams: Seq[Streamable[U]]) =
    RoundRobbin(streams.asInstanceOf[Seq[OrderedStreamable[U]]])
  
  override def makeLazyRoundRobbin[U <: T](initStreams: List[Streamable[U]]) =
    LazyRoundRobbin[U](initStreams.asInstanceOf[List[OrderedStreamable[U]]])
      
  def getFinalStream(streamable: Streamable[T]) = 
    streamable match {
      case os: OrderedStreamable[_] =>
        fine("returning ordered streamable")
        os.getStream zip os.getValues.map(_.toFloat)
      case _: Streamable[_] =>
        fine("returning unordered streamable")
        streamable.getStream zip Stream.continually(0f)
    }
}