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