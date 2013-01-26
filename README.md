# scalatra-linkeddata #

## Build & Run ##

```sh
$ cd scalatra-linkeddata
$ ./sbt
> container:start
> browse
```

## Supported formats

Supported formats are currently: Turtle, RDF/XML and JSON

Via content negotiation:

```sh
curl --header "Accept: text/turtle" http://localhost:8080/Germany
curl --header "Accept: application/rdf+xml" http://localhost:8080/Germany
curl --header "Accept: application/json" http://localhost:8080/Germany
```

Or via formats query parameter:

```sh
curl http://localhost:8080/Germany\?format\=turtle
curl http://localhost:8080/Germany\?format\=rdfxml
curl http://localhost:8080/Germany\?format\=json
```

Instead of `Germany` you can try other countries as well, see `fao-geo-ont.xml` for more information.
