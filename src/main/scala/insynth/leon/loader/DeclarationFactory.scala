package insynth.leon.loader

import insynth.leon.{ LeonDeclaration => Declaration, ReconstructionExpression,
  ErrorExpression, UnaryReconstructionExpression, ImmediateExpression }
import insynth.engine.InitialEnvironmentBuilder
import insynth.structures.{ SuccinctType => InSynthType, Variable => InSynthVariable, _}
import insynth.leon.TypeTransformer

import leon.purescala.Definitions.{ Program, FunDef, ClassTypeDef, VarDecl }
import leon.purescala.TypeTrees.{ TypeTree => LeonType, _ }
import leon.purescala.Common.{ Identifier, FreshIdentifier }
import leon.purescala.Trees._

object DeclarationFactory {
  
  val WEIGHT_FOR_LITERALS = 5f
     
  val listOfInstanceTypes = List(BooleanType, Int32Type)
  
  def makeDeclaration(expression: ReconstructionExpression, leonType: LeonType) = {
    val inSynthType = TypeTransformer(leonType)
    Declaration(expression, inSynthType, leonType)    
  }
  
  def makeLiteral(expression: ReconstructionExpression, leonType: LeonType) = {
    val inSynthType = TypeTransformer(leonType)
    Declaration(expression, inSynthType, leonType, WEIGHT_FOR_LITERALS)    
  }
  
  def makeDeclaration(expression: ReconstructionExpression, leonType: LeonType, commutative: Boolean) = {
    val inSynthType = TypeTransformer(leonType)
    Declaration(expression, inSynthType, leonType)    
  }
  
  def makeArgumentDeclaration(varDecl: VarDecl) = {
    val leonType = varDecl.getType
    val inSynthType = TypeTransformer(leonType)
    Declaration(ImmediateExpression(varDecl.id, Variable(varDecl.id)), inSynthType, leonType)    
  }
  
  def makeInheritance(from: ClassTypeDef, to: ClassTypeDef) = {
    val expr = UnaryReconstructionExpression(from + " is " + to, identity[Expr] _)
    val inSynthType = Arrow(TSet(TypeTransformer(from)), TypeTransformer(to))
    Declaration(expr, inSynthType, Untyped)    
  }
  
  def makeInheritance(from: ClassType, to: ClassType) = {
    val expr = UnaryReconstructionExpression("Inheritane(" + from.classDef + "<<is>>" + to.classDef + ")", identity[Expr] _)
    val inSynthType = Arrow(TSet(TypeTransformer(from)), TypeTransformer(to))
    Declaration(expr, inSynthType, FunctionType(List(from), to))    
  }  
  
  def makeDeclarationForTypes(types: List[LeonType])(makeFunction: LeonType => _) =   
    for (tpe <- types)
	    makeFunction(tpe)
	    	    
  def yieldDeclarationForTypes(types: List[LeonType])(makeFunction: LeonType => Declaration) =   
    for (tpe <- types)
	    yield makeFunction(tpe)
	    
  def makeAbstractDeclaration(inSynthType: InSynthType) = {
      throw new RuntimeException
    Declaration(getAbsExpression(inSynthType), inSynthType, null)
  }
    	
  // define this for abstract declarations
  def getAbsExpression(inSynthType: InSynthType) =
    ErrorExpression
}