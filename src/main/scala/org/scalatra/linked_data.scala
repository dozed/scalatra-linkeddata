package org.scalatra

import org.scalatra._
import org.scalatra.json._

import com.hp.hpl.jena.rdf.model._
import com.hp.hpl.jena.query._

import java.io._
import javax.servlet.http.HttpServletRequest

import scala.collection.JavaConversions._

import org.json4s._
// import org.json4s.JsonDSL._

object LinkedDataSupport {
  val SparqlQueryKey = "org.scalatra.linkeddata.SparqlQuery"
}

sealed trait QueryResult
case class SelectResult(solutions: List[QuerySolution]) extends QueryResult
case class DescribeResult(model: Model) extends QueryResult
case class ConstructResult(model: Model) extends QueryResult
case class AskResult(result: Boolean) extends QueryResult
object NullResult extends QueryResult

trait LinkedDataSupport extends ApiFormats with JacksonJsonSupport {
  self: ScalatraServlet =>

  protected implicit val jsonFormats: Formats = DefaultFormats

  import LinkedDataSupport._

  def model: Model

  org.openjena.riot.RIOT.init()

  formats("turtle") = "text/turtle"
  formats("rdf+xml") = "application/rdf+xml"
  formats("ld+json") = "application/ld+json"
  formats("rdf+json") = "application/rdf+json"
  formats("sparql-query") = "application/sparql-query"
  formats("sparql-results+xml") = "application/sparql-results+xml"
  formats("sparql-results+json") = "application/sparql-results+json"

  mimeTypes("text/turtle") = "turtle"
  mimeTypes("application/rdf+xml") = "rdf+xml"
  mimeTypes("application/ld+json") = "ld+json"
  mimeTypes("application/rdf+json") = "rdf+json"
  mimeTypes("application/sparql-query") = "sparql-query"
  mimeTypes("application/sparql-results+xml") = "sparql-results+xml"
  mimeTypes("application/sparql-results+json") = "sparql-results+json"

  val jenaWriterFormats = Map(
    "turtle" -> ("TURTLE", "text/turtle"),
    "rdf+json" -> ("RDF/JSON", "application/json"),  // "application/rdf+json"
    "xml" -> ("RDF/XML", "application/rdf+xml"))

  override protected def invoke(matchedRoute: MatchedRoute) = {
    withRouteMultiParams(Some(matchedRoute)) {
      if (shouldParseSparqlQuery) {
        request(SparqlQueryKey) = parseSparqlQuery.asInstanceOf[AnyRef]
      }
      super.invoke(matchedRoute)
    }
  }

  private def shouldParseSparqlQuery = (requestContentType == "sparql-query")

  def sparqlQueryString(implicit request: HttpServletRequest): String = request.get(SparqlQueryKey).map(_.asInstanceOf[String]) getOrElse {
    var bd: String = ""
    if (shouldParseSparqlQuery) {
      bd = parseSparqlQuery
      request(SparqlQueryKey) = bd.asInstanceOf[AnyRef]
    }
    bd
  }

  private def parseSparqlQuery = {
    request.body
  }

  def sparqlQuery(q: String): QueryResult = {
    request(SparqlQueryKey) = q
    sparqlQuery
  }

  def sparqlQuery: QueryResult = {
    val query = QueryFactory.create(sparqlQueryString)
    val qexec = QueryExecutionFactory.create(query, model)

    if (query.isSelectType) {
      var solutions = List[QuerySolution]()
      try {
        val results = qexec.execSelect
        solutions = results.toList
      } finally { qexec.close }
      SelectResult(solutions)
    } else if (query.isDescribeType) {
      DescribeResult(qexec.execDescribe)
    } else if (query.isConstructType) {
      ConstructResult(qexec.execConstruct)
    } else if (query.isAskType) {
      AskResult(qexec.execAsk)
    } else {
      NullResult
    }
  }

  def requestContentType = {
    request.contentType flatMap (ct => formatForMimeTypes(ct)) getOrElse format
  }

  override def renderPipeline = ({
    case stmts: StmtIterator => writeStatements(stmts)
    case SelectResult(solutions) => writeQuerySolutions(solutions)
    case DescribeResult(model) => writeStatements(model.listStatements)
    case ConstructResult(model) => writeStatements(model.listStatements)
    case AskResult(result) => writeBooleanResult(result)
  }:RenderPipeline) orElse super.renderPipeline

  private def writeQuerySolutions(l: List[QuerySolution]) = format match {
    case "sparql-results+json" => writeQuerySolutionsAsJValue(l)
    case "sparql-results+xml" => writeQuerySolutionsAsXml(l)
    case _ => writeQuerySolutionsAsXml(l)
  }

  private def writeQuerySolutionsAsXml(l: List[QuerySolution]) = {
    def valueXml(v: RDFNode) = v match {
      case b:Resource if b.isAnon => <bnode>{b.getURI}</bnode>
      case r:Resource => <uri>{r.getURI}</uri>
      case l:Literal if !l.getLanguage.isEmpty => <literal xml:lang={l.getLanguage}>{l.getValue}</literal>
      case l:Literal if l.getDatatypeURI != null => <literal datatype={l.getDatatypeURI}>{l.getValue}</literal>
      case l:Literal => <literal>{l.getValue}</literal>
    }

    def solutionXml(s: QuerySolution) = {
      <result>
      { s.varNames.map(vn => <binding name={vn}>{valueXml(s.get(vn))}</binding>) }
      </result>
    }

    // contentType = "application/sparql-results+xml"
    contentType = "application/xml"

    <sparql xmlns="http://www.w3.org/2005/sparql-results#">
      <head>
        { l.flatMap(_.varNames).toSet map { vn: String => <variable name={vn} /> } }
      </head>
      <results>
        { l map solutionXml }
      </results>
    </sparql>
  }

  private def writeQuerySolutionsAsJValue(l: List[QuerySolution]): JValue = {
    def asJValue(o: Any): JValue = o match {
      case s: String => JString(s)
      case i: Int => JInt(i)
      case d: Double => JDouble(d)
      case f: Float => JDouble(f)
      case b: Boolean => JBool(b)
      case _ => JNothing
    }

    def bindingValue(r: RDFNode): JValue = r match {
      case b:Resource if b.isAnon =>
        JObject(
          "type" -> JString("bnode") ::
          "value" -> JString(b.getURI) :: Nil)

      case r:Resource =>
        JObject(
          "type" -> JString("uri") ::
          "value" -> JString(r.getURI) :: Nil)

      case l:Literal if !l.getLanguage.isEmpty =>
        JObject(
          "type" -> JString("literal") ::
          "xml:lang" -> JString(l.getLanguage) ::
          "value" -> asJValue(l.getValue) :: Nil)

      case l:Literal if l.getDatatypeURI != null =>
        JObject(
          "type" -> JString("typed-literal") ::
          "datatype" -> JString(l.getDatatypeURI) ::
          "value" -> asJValue(l.getValue) :: Nil)

      case l:Literal => 
        JObject(
          "type" -> JString("literal") ::
          "value" -> asJValue(l.getValue) :: Nil)
    }

    def bindingJson(s: QuerySolution): JValue = {
      val vars = s.varNames.toList
      JArray(vars map { vn => JObject(vn -> bindingValue(s.get(vn))) })
    }

    // contentType = "application/sparql-results+json"
    contentType = "application/json"

    val varNames = l.flatMap(_.varNames).toSet.toList map JString.apply

    JObject("head" -> JObject("vars" -> JArray(varNames)) ::
            "results" -> JObject("bindings" -> JArray(l map bindingJson)) :: Nil)
  }

  private def writeBooleanResult(r: Boolean) = format match {
    case "sparql-results+json" =>
      // contentType = "application/sparql-results+json"
      contentType = "application/json"
      JObject("boolean" -> JBool(r))

    case _ => 
      // contentType = "application/sparql-results+xml"
      contentType = "application/xml"
      <sparql xmlns="http://www.w3.org/2005/sparql-results#">
        <boolean>{r}</boolean>
      </sparql>
  }

  private def writeStatements(stmts: StmtIterator) {
    if (format == "ld+json") {
      writeStatementsAsJsonLD(stmts)
    } else {
      val (wf, ct) = jenaWriterFormats.getOrElse(format, ("RDF/XML", "application/rdf+xml"))
      contentType = ct

      val m = ModelFactory.createDefaultModel
      m.add(stmts)
      m.write(response.getOutputStream, wf)
    }
  }

  private def writeStatementsAsJsonLD(stmts: StmtIterator) {
    // contentType = "application/ld+json"
    contentType = "application/json"
    val w = response.getWriter
    val stl = stmts.toList

    def obj(r: RDFNode) = r match {
      case r:Resource => r
      case r:Literal => r.getString
    }

    def ctx(l: Iterable[Statement]) = {
      l.groupBy(s => (s.getPredicate, s.getObject.isResource)).keys.map({
        case (p, true) =>
            """"%s": { "@id": "%s", "@type": "@id" }""".format(p.getLocalName, p)
        case (p, false) =>
            """"%s": "%s" """.format(p.getLocalName, p)
      })
    }

    stl.groupBy(_.getSubject) foreach { case (_, l) =>
      w.print("{\n")
      w.print(""""@context": { %s },""".format(ctx(l).mkString(",\n")))
      l.map { s =>
        "\"" + s.getPredicate.getLocalName + "\": \"" + obj(s.getObject) + "\""
      }.mkString(",\n").foreach(w.print)
      w.print("}\n")
    }
  }

}

