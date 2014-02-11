package insynth
package attrgrammar

import scala.collection._

import org.kiama.attribution.Attribution
import org.kiama.attribution.Attributable
import org.kiama.attribution.Attribution._
import org.kiama.attribution.Decorators._

import streams._
import insynth.streams.ordered.IntegerWeightStreamable
import reconstruction.stream._
import util.logging._
import StreamableAST._

import scala.language.postfixOps

trait Streamables[T] {

  def stream: StreamEl => Streamable[T]

  def listStream: ListStreamEl => Streamable[List[T]]

  def visited: Element => Set[Element]
  
  def cachedStreams: StreamEl => Map[StreamEl, Streamable[T]]

  // collects all recursive link from the node and its children
  // NOTE: this is done since some stream nodes are added externally and not detected by
  // the attribute grammar
  def allRecursiveLinksDownTheTree: Element => Set[Element]
  
}

class StreamablesImpl[T](_streamBuilder: StreamFactory[T]) extends Streamables[T]
  with HasLogger {

  def streamBuilder(streamEl: StreamEl) =
    if (allRecursiveLinksDownTheTree(streamEl) contains streamEl) _streamBuilder.memoized
    else _streamBuilder
    
  def streamBuilder = _streamBuilder

  type InjectionStreamValue = (T, Int)

  // streamables that can be attached with recursive edges
  type LazyStreamable = Streamable[T] with AddStreamable[T]

  def getStreamPairs(streamEl: StreamEl,
    process: Map[Element, T => T],
    combiner: PartialFunction[(Class[_], List[T]), T],
    injections: Map[Class[_], (Stream[InjectionStreamValue], Boolean)],
    streamSpecificInjections: Map[StreamEl, (Stream[InjectionStreamValue], Boolean)] = Map(),
    filters: Map[Filter, T => Boolean] = Map()) = {
    entering("getStreamPairs with injections: " + injections.map(_._2._1.mkString(",")))
    extractPairStream(
      getStreamable(streamEl, process, combiner, injections, streamSpecificInjections,
        filters))
  }

  def getStreamListPairs(streamEl: ListStreamEl,
    process: Map[Element, T => T],
    combiner: PartialFunction[(Class[_], List[T]), T],
    injections: Map[Class[_], (Stream[InjectionStreamValue], Boolean)],
    streamSpecificInjections: Map[StreamEl, (Stream[InjectionStreamValue], Boolean)] = Map(),
    filters: Map[Filter, T => Boolean] = Map()) = {
    extractPairStream(
      getStreamableList(streamEl, process, combiner, injections, streamSpecificInjections,
        filters))
  }

  def getStream(streamEl: StreamEl,
    process: Map[Element, T => T],
    combiner: PartialFunction[(Class[_], List[T]), T],
    injections: Map[Class[_], (Stream[InjectionStreamValue], Boolean)],
    streamSpecificInjections: Map[StreamEl, (Stream[InjectionStreamValue], Boolean)] = Map(),
    filters: Map[Filter, T => Boolean] = Map()) = {
    getStreamable(streamEl, process, combiner, injections, streamSpecificInjections,
      filters).getStream
  }

  def getStreamable(streamEl: StreamEl,
    process: Map[Element, T => T],
    combiner: PartialFunction[(Class[_], List[T]), T],
    injections: Map[Class[_], (Stream[InjectionStreamValue], Boolean)],
    streamSpecificInjections: Map[StreamEl, (Stream[InjectionStreamValue], Boolean)] = Map(),
    filters: Map[Filter, T => Boolean] = Map()) = {

    initialize(streamEl)
    this.combiner = combiner
    this.process = process
    this.injections = injections
    this.specificInjections = streamSpecificInjections
    this.filters = filters

    val transformed = stream(streamEl)

    postProcess

    transformed
  }

  def getStreamableList(streamEl: ListStreamEl,
    process: Map[Element, T => T],
    combiner: PartialFunction[(Class[_], List[T]), T],
    injections: Map[Class[_], (Stream[InjectionStreamValue], Boolean)],
    streamSpecificInjections: Map[StreamEl, (Stream[InjectionStreamValue], Boolean)] = Map(),
    filters: Map[Filter, T => Boolean] = Map()) = {

    initialize(streamEl)
    this.combiner = combiner
    this.process = process
    this.injections = injections
    this.specificInjections = streamSpecificInjections
    this.filters = filters

    val transformed = listStream(streamEl)

    postProcess

    transformed
  }

  /********************************************/
  // Attributes
  /********************************************/
  
  override def stream = _stream
  var _stream: StreamEl => Streamable[T] =
    dynAttr {
      case i: Injecter =>
        // get stream to be injected
        val (innerStream, isInfinite) =
          // get StreamEl-specific injection, otherwise class-specific
          specificInjections.getOrElse(i, injections(i.c))
        fine("(innerStream, isInfinite) is " + (innerStream, isInfinite) + " for " + i)

        // depending on whether the stream is infinite, make appropriate streamable
        if (isInfinite) {
          streamBuilder.makeSingleStream(innerStream)
        }
        else
          // NOTE: optimization, we memoize injected finite streams
//          streamBuilder.memoized.makeFiniteStream(innerStream.toVector)
          streamBuilder.memoized.makeFiniteStream(innerStream.toVector)
        
      
      // TODO optimize this!, dont do unary stream if not necessary
      case s@Single(c, inner) =>
        // create a modify function if process has this stream element 
//        if (process contains s) {
          val modify = process(s)
          streamBuilder(s).makeUnaryStream(inner -> stream, modify)
//        } else
//          inner -> stream

      case f@ Filter(c, inner, _) =>        
        // check if filter stream is needed
        if (filters.contains(f))
          streamBuilder(f).makeFilterStream(inner -> stream, filters(f))
        else
          inner -> stream

      case a @ Alternater(c, inner) =>
        // get node sets for both recursive and non-recursive edges
        val (recursiveParams, nonRecursiveParams) =
          //          inner partition { a->visited contains _ }
          (a.getRecursiveLinks, inner)

        info("recursiveParams.size=" + recursiveParams.size +
          "nonRecursiveParams.size=" + nonRecursiveParams.size)

        // transform only non-recursive 
        val nonRecursiveStreamableList =
          nonRecursiveParams map { _ -> stream }

        // if there are recursive links we need to construct lazy stream
        val thisStream =
          if (recursiveParams.isEmpty)
            streamBuilder(a).makeRoundRobbin(nonRecursiveStreamableList)
          else {
            val paramInitStream = streamBuilder(a).makeLazyRoundRobbin(nonRecursiveStreamableList.toList)

            val kv: (StreamEl, (LazyStreamable, List[StreamEl])) =
              (a, (paramInitStream, recursiveParams.toList))

            recursiveParamsMap += kv

            paramInitStream
          }

//        // TODO optimize this!, dont do unary stream if not necessary
//        val modify: T => T = (t: T) =>
//          if (process.isDefinedAt((c, t)))
//            process(c, t)
//          else
//            t
//
//        streamBuilder(a).makeUnaryStream(thisStream, modify)
        thisStream

      case c@ Combiner(clazz, inner) =>
        // make streams of lists of parameter combinations
        val paramListStream = inner -> listStream

        streamBuilder(c).makeUnaryStream(paramListStream,
          (list: List[T]) => combiner(clazz, list), _ + 1)

      case Empty =>
        streamBuilder.makeEmptyStreamable
    }

  def cachedStreams = _cachedStreams
  var _cachedStreams: StreamEl => Map[StreamEl, Streamable[T]] =
    attr {
      case t if t isRoot => Map((t, (t -> stream)))
      case t => {
        val kv = (t, (t -> stream)): (StreamEl, Streamable[T])

        t.parent[StreamEl] -> cachedStreams + kv
      }
    }

  def visited = _visited
  var _visited: Element => Set[Element] =
    //down[Element, Set[Element]]
    attr {
      case se: Element if artificialVisited contains se => {
        info("visited matched (artificialVisited contains se) for " + se )
        artificialVisited(se)
      }
      case t: Element if t isRoot => Set(t)
      case t: Element => Set(t) | t.parent[Element] -> visited
    }
  
  def listStream = _listStream
  var _listStream: ListStreamEl => Streamable[List[T]] = {
    import _streamBuilder._
    
    attr {
      case agg @ Aggregator(inner) =>
        def makeTwoElementCombinationListStream(list: List[Element]):
        	Streamable[List[T]] = {
          list match {
          	case (s1: StreamEl) :: (s2: StreamEl) :: Nil =>
          	  makeBinaryStream(s1->stream, s2->stream) {
                (el1, el2) => List(el1, el2)
              }
          	  // cannot happen
//          	case (s1: ListStreamEl) :: (s2: StreamEl) :: Nil =>
          	case (s1: StreamEl) :: (s2: ListStreamEl) :: Nil =>
          	  makeBinaryStream(s1->stream, s2->listStream) {
                (el1, l2) => el1 :: l2
              }
          	case _ => throw new RuntimeException
          }
        } 

        // make streams of lists of parameter combinations
        val paramListStream: Streamable[List[T]] =
          inner.reverse.toList match {
            case Nil => makeSingletonList(Nil)
            case List(s: StreamEl) =>
              makeUnaryStreamList(s -> stream, { el: T => List(el) })
            case List(sel: ListStreamEl) =>
              sel -> listStream
            case (sel: ListStreamEl) :: rest =>
              (rest :\ sel -> listStream) {
                case (se: StreamEl, resStream) =>
                  makeBinaryStream(se->stream, resStream) { (el, list) => el :: list }
                case (sel: ListStreamEl, resStream) =>
                  makeBinaryStream(resStream, sel->listStream) { (list1, list2) => list1 ::: list2 }
              }
            case e1 :: e2 :: rest =>
              (rest :\ makeTwoElementCombinationListStream( e2 :: e1 :: Nil )) {
                case (se: StreamEl, resStream) =>
                  makeBinaryStream(se->stream, resStream) { (el, list) => el :: list }
                case (sel: ListStreamEl, resStream) =>
                  makeBinaryStream(resStream, sel->listStream) { (list1, list2) => list1 ::: list2 }
              }
          }

        paramListStream

      case Generator(inner) =>
        val nilStream = streamBuilder.makeSingletonList(Nil)
        val genStream = inner -> stream

        val listStream = memoized.makeLazyRoundRobbinList(List(nilStream))

        // NOTE: this needs to be memoized (see the theoretical examination)
        val constructorStream =
          makeBinaryStream(listStream, genStream) { (list, el2) => list :+ el2 }

        listStream addStreamable constructorStream
        listStream.initialize

        listStream
    }
  }

  var recursiveParamsMap: mutable.Map[StreamEl, (LazyStreamable, List[StreamEl])] = _
  //  var nodeMap: MutableMap[StreamEl, Streamable[T]] = _

  var combiner: PartialFunction[(Class[_], List[T]), T] = _
  var process: Map[Element, T => T] = _
  var injections: Map[Class[_], (Stream[InjectionStreamValue], Boolean)] = _
  var specificInjections: Map[StreamEl, (Stream[InjectionStreamValue], Boolean)] = _
  var filters: Map[Filter, T => Boolean] = _

  // TODO: a hack
  var artificialVisited = mutable.Map[Element, Set[Element]]()
  var artificialChildren = mutable.Map[Element, List[Element]]()
  def computeArtificiallyVisited(streamEl: Element, visited: Set[Element], isArtificial: Boolean): Unit = {
    if (isArtificial) {
      artificialVisited += (streamEl -> (visited + streamEl))
      artificialChildren += (streamEl -> StreamableAST.children(streamEl))
    }

    if (! (visited contains streamEl) ) {
      streamEl match {
        case s@Single(c, inner) =>
          computeArtificiallyVisited(inner, visited + streamEl, isArtificial)

        case f@ Filter(c, inner, _) =>
          computeArtificiallyVisited(inner, visited + streamEl, isArtificial)
  
        case a @ Alternater(c, inner) =>
          for (recLink <- a.getRecursiveLinks)
            computeArtificiallyVisited(recLink, visited + streamEl, true)
              
          for (link <- inner)
            computeArtificiallyVisited(link, visited + streamEl, isArtificial)
  
        case c@ Combiner(clazz, inner) =>
          computeArtificiallyVisited(inner, visited + streamEl, isArtificial)
  
        case _ =>
      }
    } 
  }
  
  // initialize data for each traversal
  def initialize(streamEl: Element) = {

    // a hack
    artificialVisited = mutable.Map[Element, Set[Element]]()
    artificialChildren = mutable.Map[Element, List[Element]]()
    computeArtificiallyVisited(streamEl, Set(), false)
    info("artificialVisited in initialization: " + artificialVisited)
    
    Attribution.resetMemo
    Attribution.initTree(streamEl)

    //    nodeMap = MutableMap.empty
    recursiveParamsMap = mutable.Map.empty
  }

  // method that is called after the traversal to update recursive node children
  def postProcess = {
    for (
      (streamEl, (paramInitStream, recursiveLinks)) <- recursiveParamsMap
    ) {
      val recursiveStreams = recursiveLinks map (_ -> stream)
      assert(recursiveStreams.size > 0, "recursiveStreams.size > 0")

      paramInitStream addStreamable recursiveStreams
      assert(!paramInitStream.isInitialized, "!paramInitStream.isInitialized")

      // initialize the lazy round robbin
      //      paramInitStream.initialize
    }
    
    // try to rid of all cached values so that GC can proceed
    Attribution.resetMemo
    // reset fields to null
    _stream = null
    _listStream = null
    _visited = null
    _cachedStreams = null
    _allRecursiveLinksDownTheTree = null

    recursiveParamsMap = null
//    combiner = null
    process = null
    injections = null
    specificInjections = null
    filters = null
    artificialVisited = mutable.Map[Element, Set[Element]]()
    artificialChildren = mutable.Map[Element, List[Element]]()
  }

  def extractPairStream(s: Streamable[_]) =
    s match {
      case os: IntegerWeightStreamable[_] =>
        fine("returning ordered streamable")
        os.getValuedStream
      case us: Streamable[_] =>
        fine("returning unordered streamable")
        us.getStream zip Stream.continually(0)
    }

  def allRecursiveLinksDownTheTree = _allRecursiveLinksDownTheTree
  var _allRecursiveLinksDownTheTree: Element => Set[Element] = {
  
    def children(e: Element) = {
      if (artificialChildren contains e)
        artificialChildren(e)
      else e.children.toList
    }
    
    attr {
      case a @ Alternater(c, inner) =>
        val allLinks = (inner ++ a.getRecursiveLinks)
        val recursive: Set[Element] = (allLinks.filter {
          case link: Element => a->visited contains link
          case _ => false
        }).toSet

        info("@ Alternater(%s) a->visited=%s, allLinks=%s, recursive=%s, inner=%s".
          format(a, a->visited, allLinks, recursive.toString, inner) )

        // alternater can have recursive links only in the returned collection
        ( recursive /:
          // for the alternater, explore also the additionally added, recursive links
          allLinks ) {
          	case (res, child: Element) if ! (a->visited contains child) =>
              res | allRecursiveLinksDownTheTree(child)
          	case (res, _) => res
          }
      case a @ Aggregator(inner) =>
        val recursiveLinks = inner.filter( a->visited contains _ ).toSet
        info("Aggregator(%s) recursiveLinks=%s, a->visited=%s, inner=%s".
          format(a, recursiveLinks, a->visited, inner))
        // aggregator can have recursive links as children
        (recursiveLinks /: children(a)) {
        	// skip recursive links when traversing children
        	case (res, child: Element) if ! (a->visited contains child) =>
            res | allRecursiveLinksDownTheTree(child)
        	case (res, _) => res
        }
      case t: Element => {
        val recursiveLinks = children(t) filter {
          case child: StreamEl => t->visited contains child
          case _ => false
        }
        info("Element(%s) recursiveLinks=%s, t->visited=%s, t.children=%s".
          format(t, recursiveLinks, t->visited, children(t)))
        // traverse any other element by means of its children, skip the recursive links
        (recursiveLinks.toSet.asInstanceOf[Set[Element]] /: children(t)) {
          case (res, child: Element) if ! (recursiveLinks contains child)  =>
            res | (child -> allRecursiveLinksDownTheTree)
        	case (res, _) => res
        }
      }
    }
  }

}