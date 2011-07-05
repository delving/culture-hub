# play-services

Proof of concept of a services application written with play-scala.

This will eventually demonstrate things such as authentication & authorization, content-type based rendering, etc.

## Getting started

- run `sh setup-play.sh` in order to install play
- from the `play-services` directory, run `play dependencies` in order to download and install dependencies and then run the application via `play run`
- copy `conf/application.conf.template`to `conf/application.conf`and configure the required properties at the end of the file
- in order to use the project in IDEA (until there will be plugin support for it), run `play idealize` to generate a module, then create a new project (without module) and import the generated module
- an example user is `bob@gmail.com` with password `bob`

- to use Apache Solr go to `extras/solr-server` and run `java -jar start.jar`.


## Installing system dependencies

### MAC OS X

    brew install mongodb
    brew install graphicsmagick
    # todo add other dependencies.