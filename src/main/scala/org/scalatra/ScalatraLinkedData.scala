package org.scalatra

import org.scalatra._
import scalate.ScalateSupport
import scala.collection.JavaConversions._

import com.hp.hpl.jena.rdf.model._
import com.hp.hpl.jena.query._

import java.io._

class ScalatraLinkedData extends ScalatraServlet with ScalateSupport with LinkedDataSupport {

  val model = ModelFactory.createDefaultModel
  val reader = model.getReader
  reader.read(model, new InputStreamReader(new FileInputStream("fao-geo-ont.xml")), null)

  def faoUrl(key: String) = "http://www.fao.org/countryprofiles/geoinfo/geopolitical/resource/%s".format(key)

  val rdfType = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
  val selfGoverning = model.getResource(faoUrl("self_governing"))

  val defaultModel: Model = model
  override def model(key: String): Option[Model] = None

  get("/country/:id") {
    val url = faoUrl(params("id"))
    val res = model.getResource(url)
    val stmts = model.listStatements(res, null, null)
    stmts
  }

  post("/sparql", requestContentType == "sparql-query") {
    val querySolutions: QueryResult = sparqlQuery
    querySolutions
  }

  get("/sparql", !params.contains("query")) {
    jade("sparql")
  }

  get("/sparql", params.contains("query")) {
    val queryString: String = params("query")
    val result = sparqlQuery(queryString)

    if (format == "html") {
      renderAsHtml(result, queryString)
    } else {
      result
    }
  }

  private def renderAsHtml(result: QueryResult, queryString: String) = result match {
    case SelectResult(results) =>
      jade("sparql-results",
        "results" -> results,
        "query" -> queryString)
    case DescribeResult(model) => result
    case ConstructResult(model) => result
    case AskResult(result) => result
    case NullResult => 
  }

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    // Try to render a ScalateTemplate if no route matched
    findTemplate(requestPath) map { path =>
      contentType = "text/html"
      layoutTemplate(path)
    } orElse serveStaticResource() getOrElse resourceNotFound()
  }
}
