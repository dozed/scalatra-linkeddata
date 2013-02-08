# scalatra-linkeddata #

## Build & Run ##

```sh
$ cd scalatra-linkeddata
$ ./sbt
> container:start
> browse
```

## Supported formats

Supported formats are currently: Turtle, RDF/XML, RDF/JSON and JSON-LD

Via content negotiation:

```sh
curl --header "Accept: text/turtle" http://localhost:8080/Germany
curl --header "Accept: application/rdf+xml" http://localhost:8080/Germany
curl --header "Accept: application/rdf+json" http://localhost:8080/Germany
curl --header "Accept: application/ld+json" http://localhost:8080/Germany
```

Or via formats query parameter:

```sh
curl http://localhost:8080/Germany\?format\=turtle
curl http://localhost:8080/Germany\?format\=rdfxml
curl http://localhost:8080/Germany\?format\=rdf%2Bjson
curl http://localhost:8080/Germany\?format\=ld%2Bjson
```

Instead of `Germany` you can try other countries as well, see `fao-geo-ont.xml` for more information.

## SPARQL Queries

[SPARQL 1.1 Protocol](http://www.w3.org/TR/sparql11-protocol/) is partially supported. Currently only the direct `POST` variant works out-of-the-box:

```sh
curl -v -d """
  SELECT ?x ?p ?y
  WHERE {
    ?x <http://www.fao.org/countryprofiles/geoinfo/geopolitical/resource/codeISO2>  \"DE\";
       ?p ?y
}""" -H "Content-type: application/sparql-query" http://localhost:8080/sparql
```

An incoming request with content type `application/sparql-query` will set the `format` to `sparql-query`. This can be used to define routes.

There are currently two useful functions for queries. The `sparqlQueryString` function returns the query as `String`. And the `doSparqlQuery` function executes the query against the model and returns a `List[QuerySolution]`.

```scala
post("/sparql", format == "sparql-query") {
  val queryString = sparqlQueryString
  // val queryString = """SELECT ?x ?p ?y WHERE { ?x  <http://www.w3.org/2001/vcard-rdf/3.0#FN>  "John Smith"; ?p ?y }"""
  println(queryString)

  val querySolutions: List[QuerySolution] = doSparqlQuery
  querySolutions foreach { soln =>
    println(soln)
    // RDFNode x = soln.get("varName")
    // Resource r = soln.getResource("VarR")
    // Literal l = soln.getLiteral("VarL")
  }
}
```