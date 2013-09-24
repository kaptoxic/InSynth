package insynth.common

import scala.collection.mutable.{ Map => MutableMap, Set => MutableSet }
import org.junit.Assert._
import org.junit._

import insynth.structures._
import insynth.testdomain.{ TestQueryBuilder => QueryBuilder, _ }

import scala.language.implicitConversions

object CommonProofTrees {
  implicit def typeToList(typ: DomainType) = List(typ)
  implicit def declToList(dec: TestDeclaration) = List(dec)
  implicit def nodeToSet(n: SimpleNode) = MutableSet(n)
  implicit val transform = DomainType.toSuccinctType _

  import CommonDeclarations._
  import CommonDomainTypes._

  val intNode =
    new SimpleNode(
      List(intDeclaration),
      MutableMap.empty)
  
  def exampleBoolToInt = {
    val queryBuilder = new QueryBuilder(typeInt)

    val query = queryBuilder.getQuery

    val queryDeclaration = query.getDeclaration

    val getBooleanNode =
      new SimpleNode(
        List(booleanDeclaration),
        MutableMap.empty)

    val getIntNode =
      new SimpleNode(
        List(functionBoolToIntDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Boolean") ->
            new ContainerNode(
              MutableSet(getBooleanNode))))

    val queryNode =
      new SimpleNode(
        List(queryDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Int") ->
            new ContainerNode(
              MutableSet(getIntNode))))

    (queryNode, query)
  }

  def exampleIntToInt = {
    val queryBuilder = new QueryBuilder(typeInt)

    val query = queryBuilder.getQuery

    val queryDeclaration = query.getDeclaration

    val getIntNode =
      new SimpleNode(
        List(functionIntToIntDeclaration),
        MutableMap())

    getIntNode.getParams +=
      (
        Const("Int") ->
        new ContainerNode(
          MutableSet(intNode, getIntNode)))

    val queryNode =
      new SimpleNode(
        List(queryDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Int") ->
            new ContainerNode(
              MutableSet(getIntNode))))

    queryNode
  }
  
  
  def exampleIntToIntBool = {
    val queryBuilder = new QueryBuilder(typeInt)

    val query = queryBuilder.getQuery

    val queryDeclaration = query.getDeclaration
                    
    val getBooleanNode =
      new SimpleNode(
        List(booleanDeclaration),
        MutableMap.empty)

    val getIntNodeFromBoolean =
      new SimpleNode(
        List(functionBoolToIntDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Boolean") ->
            new ContainerNode(
              MutableSet(getBooleanNode))))

    val getIntNodeFromIntToInt =
      new SimpleNode(
        List(functionIntToIntDeclaration),
        MutableMap())

    getIntNodeFromIntToInt.getParams +=
      (
        Const("Int") ->
        new ContainerNode(
          MutableSet(getIntNodeFromBoolean, getIntNodeFromIntToInt)))

    val queryNode =
      new SimpleNode(
        List(queryDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Int") ->
            new ContainerNode(
              MutableSet(getIntNodeFromBoolean, getIntNodeFromIntToInt))))

    queryNode
  }
  
  
  def exampleIntToIntBoth = {
    val queryBuilder = new QueryBuilder(typeInt)

    val query = queryBuilder.getQuery

    val queryDeclaration = query.getDeclaration
        
    val intNode =
      new SimpleNode(
        List(intDeclaration),
        MutableMap.empty)
                    
    val getBooleanNode =
      new SimpleNode(
        List(booleanDeclaration),
        MutableMap.empty)

    val getIntNodeFromBoolean =
      new SimpleNode(
        List(functionBoolToIntDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Boolean") ->
            new ContainerNode(
              MutableSet(getBooleanNode))))

    val getIntNodeFromIntToInt =
      new SimpleNode(
        List(functionIntToIntDeclaration),
        MutableMap())

    getIntNodeFromIntToInt.getParams +=
      (
        Const("Int") ->
        new ContainerNode(
          MutableSet(intNode, getIntNodeFromBoolean, getIntNodeFromIntToInt)))

    val queryNode =
      new SimpleNode(
        List(queryDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Int") ->
            new ContainerNode(
              MutableSet(getIntNodeFromBoolean, getIntNodeFromIntToInt))))

    queryNode
  }
  
  def exampleFunctionIntToInt = {
    import CommonDomainTypes._
    implicit val transform = DomainType.toSuccinctType _

    val queryBuilder = new QueryBuilder(Function(List(typeInt), typeInt))

    val query = queryBuilder.getQuery

    val queryDeclaration = query.getDeclaration
                    
    val getBooleanNode =
      new SimpleNode(
        List(booleanDeclaration),
        MutableMap.empty)

    val getIntNodeFromBoolean =
      new SimpleNode(
        List(functionBoolToIntDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Boolean") ->
            new ContainerNode(
              MutableSet(getBooleanNode))))

    val getIntNodeFromIntToInt =
      new SimpleNode(
        List(functionIntToIntDeclaration),
        MutableMap())

    getIntNodeFromIntToInt.getParams +=
      (
        Const("Int") ->
        new ContainerNode(
          MutableSet(getIntNodeFromBoolean, getIntNodeFromIntToInt)))

    val queryNode =
      new SimpleNode(
        List(queryDeclaration),
        MutableMap( // for each parameter type - how can we resolve it
          Const("Int") ->
            new ContainerNode(
              MutableSet(getIntNodeFromBoolean, getIntNodeFromIntToInt))))

    queryNode
  }

  // InSynth example trees

  val intLeafNode =
    new SimpleNode(
      List(intDeclaration),
      MutableMap.empty)
  
	//***************************************************
	// Goals
	//	find expression of type: Boolean
	//	expression: query(m1(this, m2(this), m4(this)))
	//	code:
	// 	class A {
	//  	def m1(f: Int=>String, c:Char): Boolean
	//  	def m2(a: Int): String
	//  	def m3(a: Long): String
	//  	def m4(): Char
	//  	def m5(a: Int): Long
	//  	def m6(): String
	//  	def test() {
	//    		val b:Bool = ?synthesize?
	//  	}
	//	}
	//***************************************************
  def buildComplexTree = {
    import CommonDomainTypes.BuildComplexTree._
    import CommonDeclarations.BuildComplexTree._
    implicit def typeToList(typ: DomainType) = List(typ)
	  
	  //************************************
	  // InSynth proof trees
	  //************************************
	  // goal:ClassA object, type:ClassA
	  // expression: this	  
	  val thisNode = new SimpleNode(
	      objectADeclaration, MutableMap()
      )
	    
	  // goal:Char, type:Unit→Char
	  // expression: m4(this)	  
	  val m4Node = new SimpleNode(
	      m4Declaration,
	      MutableMap(
	          transform(objectA) -> new ContainerNode(MutableSet(thisNode))
          )
      )
      
      // goal:(Int→String), type:(Int→String)
	  // expression: m2(this)
	  val m2Node = new SimpleNode(
	      m2Declaration,
	      MutableMap(
	          transform(objectA) -> new ContainerNode(MutableSet(thisNode)),
	          transform(typeInt) ->
	          	new ContainerNode(MutableSet(new SimpleNode(
	          	    { 
	          	      val dec = new TestDeclaration(typeInt); dec	          	    	
	          	    }, MutableMap.empty
          	    )))
          )
      )      
      
      // goal:String, type:(A→String)
	  // expression: m6(this)
	  val m6Node = new SimpleNode(
	      m6Declaration,
	      MutableMap(
	          transform(objectA) -> new ContainerNode(MutableSet(thisNode))
          )
      )
            
      // goal: Long, type:(Int→Long)
	  // expression: m5(this, _)
	  val m5Node = new SimpleNode(
	      m5Declaration,
	      MutableMap(
	          transform(objectA) -> new ContainerNode(MutableSet(thisNode)),
	          transform(typeInt) -> new ContainerNode( 
	          	MutableSet( new SimpleNode(
	          	    { 
	          	      val dec = new TestDeclaration(typeInt); dec	          	    	
	          	    }, MutableMap.empty
          	    ) )
	          )
          )
      )
      
      // goal:(Int→String), type:(Long→String)
	  // expression: Int => m3(this, m5(this, _))
	  val composeNode = new SimpleNode(
	      m3Declaration,
	      MutableMap(
	          transform(objectA) -> new ContainerNode(MutableSet(thisNode)),
	          transform(typeLong) -> new ContainerNode(MutableSet(m5Node))
          )
      )
	    
	  // goal:Boolean, type:List((Int→String),Char)→Boolean
	  // expression: m1(this, 
      //				m2(this) |  m3(this) ∘ m5(this) | Int→m6(this), 
	  //				m4(this))	  
	  val m1Node = new SimpleNode(
	      m1Declaration,
	      MutableMap(
	          transform(typeChar) -> new ContainerNode(MutableSet(m4Node)),
	          transform(Function(typeInt, typeString)) ->
	          	new ContainerNode( 
	          	    MutableSet(composeNode, m2Node, m6Node)
          	    ),
	          transform(objectA) -> new ContainerNode(MutableSet(thisNode))
          )
      )
	  
      // goal:⊥, type:Boolean→⊥	    
      // expression: query(		m1(this,
	  //			m2(this) |  m3(this) ∘ m5(this) | Int→m6(this), 
	  //			m4(this)	)):⊥
	  val queryNode = 
	    new SimpleNode(
	  	  queryDeclaration,
	  	  MutableMap( // for each parameter type - how can we resolve it
	  	      transform(typeBoolean) ->
	  	      new ContainerNode(
	  	          MutableSet(m1Node)
	            )
	        ) 
	    )
	    
	  queryNode
  }
  
  def buildLighterComplexTree = {
    import CommonDomainTypes.BuildLighterComplexTree._
    import CommonDeclarations.BuildLighterComplexTree._
	  
    val queryBuilder = new QueryBuilder(typeBoolean)
    val queryDeclaration = queryBuilder.getQuery.getDeclaration

	  val intLeafNode = new SimpleNode(intLeafDeclaration, MutableMap.empty)
    val m2Node = new SimpleNode(m2Declaration, MutableMap(
      transform(typeInt) -> new ContainerNode(MutableSet(intLeafNode))
    ))

	  val m1Node = new SimpleNode(
	      m1Declaration,
	      MutableMap(
	          transform(Function(typeInt, typeString)) ->
	          	new ContainerNode(MutableSet(m2Node))
          )
      )
	  
	  val queryNode = 
	    new SimpleNode(
	  	  queryDeclaration,
	  	  MutableMap( // for each parameter type - how can we resolve it
	  	      transform(typeBoolean) ->
	  	      new ContainerNode(
	  	          MutableSet(m1Node)
	            )
	        ) 
	    )
	    
	  queryNode
  }

	//***************************************************
  // exercise outputting all variable (name) combinations
	//	find expression of type: Int => Int => String
	//	code:	def m1: Int => String
	//	expression: query(m1(var_1) | m1(var_2))

  def buildMultipleVarTree = {
    import CommonDomainTypes.BuildMultipleVarTree._
    import CommonDeclarations.BuildMultipleVarTree._
	  
    val m1Node = new SimpleNode(
  		m1Declaration,
      MutableMap( transform(typeInt) -> new ContainerNode(intLeafNode) )
    )

	  val queryNode = 
	    new SimpleNode(
	  	  queryDeclaration,
	  	  MutableMap( // for each parameter type - how can we resolve it
	  	      transform(queryType) ->
	  	      new ContainerNode(
	  	          MutableSet(m1Node)
	            )
	        ) 
	    )
	    
	  queryNode
  }

}