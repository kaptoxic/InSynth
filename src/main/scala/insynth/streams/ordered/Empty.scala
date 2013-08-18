package insynth.streams.ordered

import insynth.streams.unordered.{ Empty => UnordEmpty }

object Empty extends OrderedSizeStreamable[Nothing] {
    
  override def isInfinite = false
  override def getStream = Stream.empty  
  
  override def getValues = Stream.empty
  
}