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

  def doSparqlQuery: List[QuerySolution] = {
    val query = QueryFactory.create(sparqlQueryString)
    val qexec = QueryExecutionFactory.create(query, model)
    var solutions = List[QuerySolution]()
    try {
      val results = qexec.execSelect
      solutions = results.toList
    } finally { qexec.close() ; }
    solutions
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

