package org.scalatra

import org.scalatra._

import com.hp.hpl.jena.rdf.model._
import java.io._

import scala.collection.JavaConversions._

trait LinkedDataSupport extends ApiFormats {
  self: ScalatraServlet =>

  org.openjena.riot.RIOT.init()
  formats("turtle") = "text/turtle"
  formats("rdfxml") = "application/rdf+xml"
  formats("ld+json") = "application/ld+json"
  formats("rdf+json") = "application/rdf+json"

  mimeTypes("text/turtle") = "turtle"
  mimeTypes("application/rdf+xml") = "rdfxml"
  mimeTypes("application/ld+json") = "ld+json"
  mimeTypes("application/rdf+json") = "rdf+json"

  val jenaWriterFormats = Map(
    "turtle" -> ("TURTLE", "text/turtle"),
    "rdf+json" -> ("RDF/JSON", "application/json"),  // "application/rdf+json"
    "xml" -> ("RDF/XML", "application/rdf+xml"))

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

