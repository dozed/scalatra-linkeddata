package org.scalatra

import org.scalatra._

import com.hp.hpl.jena.rdf.model._
import com.hp.hpl.jena.query._

import java.io._
import javax.servlet.http.HttpServletRequest

import scala.collection.JavaConversions._

object LinkedDataSupport {
  val SparqlQueryKey = "org.scalatra.linkeddata.SparqlQuery"
}

trait LinkedDataSupport extends ApiFormats {
  self: ScalatraServlet =>

  import LinkedDataSupport._

  def model: Model

  org.openjena.riot.RIOT.init()
  formats("turtle") = "text/turtle"
  formats("rdfxml") = "application/rdf+xml"
  formats("ld+json") = "application/ld+json"
  formats("rdf+json") = "application/rdf+json"
  formats("sparql-query") = "application/sparql-query"

  mimeTypes("text/turtle") = "turtle"
  mimeTypes("application/rdf+xml") = "rdfxml"
  mimeTypes("application/ld+json") = "ld+json"
  mimeTypes("application/rdf+json") = "rdf+json"
  mimeTypes("application/sparql-query") = "sparql-query"

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

  private def shouldParseSparqlQuery = (format == "sparql-query")

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

  def sparqlQuery(q: String): List[QuerySolution] = {
    request(SparqlQueryKey) = q
    sparqlQuery
  }

  def sparqlQuery: List[QuerySolution] = {
    val query = QueryFactory.create(sparqlQueryString)
    val qexec = QueryExecutionFactory.create(query, model)
    var solutions = List[QuerySolution]()
    try {
      val results = qexec.execSelect
      solutions = results.toList
    } finally { qexec.close }
    solutions
  }

  private def solutionsXml(l: List[QuerySolution]) = {
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

    <sparql xmlns="http://www.w3.org/2005/sparql-results#">
      <head>
        { l.flatMap(_.varNames).toSet map { vn: String => <variable name={vn} /> } }
      </head>
      <results>
        { l map solutionXml }
      </results>
    </sparql>
  }

  override def renderPipeline = ({
    case stmts: StmtIterator =>

      if (format == "ld+json") {
        writeJsonLd(stmts)
      } else {
        val (wf, ct) = jenaWriterFormats.getOrElse(format, ("RDF/XML", "application/rdf+xml"))
        contentType = ct

        val m = ModelFactory.createDefaultModel
        m.add(stmts)
        m.write(response.getOutputStream, wf)
      }

    case solutions: List[QuerySolution] =>
      // contentType = "application/sparql-results+xml"
      contentType = "application/xml"
      solutionsXml(solutions)

  }:RenderPipeline) orElse super.renderPipeline

  def writeJsonLd(stmts: StmtIterator) {
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

