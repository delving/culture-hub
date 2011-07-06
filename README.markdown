# play-services

Proof of concept of a services application written with play-scala.

This will eventually demonstrate things such as authentication & authorization, content-type based rendering, etc.

## Getting started

- run `sh setup-play.sh` in order to install play. Make sure you add the play directory to your shell path after installation so it can be found in further steps.
- from the `play-services` directory, run `sh setup.sh` in order to setup the application. You may need to run `play dependencies` later on by hand in order to download and install additional dependencies.
- configure the settings at the end of `conf/application.conf`
- run the application via `play run`
- in order to use the project in IDEA (until there will be plugin support for it), run `play idealize` to generate a module, then create a new project (without module) and import the generated module
- an example user is `bob@gmail.com` with password `secret`
- to use Apache Solr go to `extras/solr-server` and run `java -jar start.jar`.


## Installing system dependencies

### MAC OS X

    brew install mongodb
    brew install graphicsmagick
    # todo add other dependencies.

## Notes

### Legacy Services module

Legacy Services are kept in a separate play module under /legacyServices which is linked via a symbolic link created
by the `setup.sh` script. Do *not* run `play depedendencies` with the `--sync` option as this will erase the module
(or at least did so in earlier versions). This is because play thinks it will get back the module from a module
repository which is not the case for local modules as this one.

Classes under `legacyServices/src/play/modules/legacyServices` are available in the classpath of the application and are not
instrumented by play. They can be used directly in the main application controllers. Additionally, bootstrapping tasks for
the plugin can be done in the `LegacyPlugin` class.

Play modules are not automatically compiled and reloaded, so whenever you do changes there, compile the plugin with

    ant -Dplay.path=/path/to/play

