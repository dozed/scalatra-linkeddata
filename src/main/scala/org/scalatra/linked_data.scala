package org.scalatra

import org.scalatra._

import com.hp.hpl.jena.rdf.model._
import java.io._

trait LinkedDataSupport extends ApiFormats {
  self: ScalatraServlet =>

  org.openjena.riot.RIOT.init()
  formats("turtle") = "text/turtle"
  mimeTypes("text/turtle") = "turtle"
  mimeTypes("application/rdf+xml") = "rdfxml"

  val jenaWriterFormats = Map(
    "turtle" -> ("TURTLE", "text/turtle"),
    "json" -> ("RDF/JSON", "application/json"),
    "xml" -> ("RDF/XML", "application/rdf+xml"))

  override def renderPipeline = ({
    case stmts: StmtIterator =>
      val (wf, ct) = jenaWriterFormats.getOrElse(format, ("RDF/XML", "application/xml"))
      contentType = ct

      val m = ModelFactory.createDefaultModel
      m.add(stmts)
      m.write(response.getOutputStream, wf)
  }:RenderPipeline) orElse super.renderPipeline

}

