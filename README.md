# tidelift-api

Instructions: https://gist.github.com/katzj/7f2a895efa485084074b492b4af88560



### Run the application locally
(assumes you have [Leiningen](https://leiningen.org/) installed)

`lein ring server`

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Notes


Uses [Compojure API](https://github.com/metosin/compojure-api) which autogenerates interactive Swagger 2.0 docs which you can
view at http://localhost:3001
