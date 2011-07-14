# play-services

Proof of concept of a services application written with play-scala.

This will eventually demonstrate things such as authentication & authorization, content-type based rendering, etc.

## Getting started

- run `sh setup-play-patched.sh` in order to install play. Make sure you add the play directory to your shell path after installation so it can be found in further steps.
- from the `play-services` directory, run `sh setup.sh` in order to setup the application. You may need to run `play dependencies` later on by hand in order to download and install additional dependencies.
- configure the settings at the end of `conf/application.conf`, i.e. create custom entries when you need them (e.g. `%manu.image.graphicsmagic.cmd=/opt/local/bin/gm`)
- run the application via `play run --%youruser`. "youruser" is the key you used for your custom properties in `conf/application.conf
- in order to use the project in IDEA (until there will be plugin support for it), run `play idealize` to generate a module, then create a new project (without module) and import the generated module
- an example user is `bob@gmail.com` with password `secret`
- to use Apache Solr go to `extras/solr-server` and run `java -jar start.jar`.


## Installing system dependencies

### MAC OS X

    brew install mongodb
    brew install graphicsmagick
    # todo add other dependencies.

## Notes

### Play installation - Play versions

As Play has some issues (just as any other frameworks) it makes sense, during development, to use the latest development version.
This way we can also use patches that did not yet make it into a release. The `setup-play-patched.sh` takes care of checking out the development branch and to apply patches for issues we may find.
For production we then use the newly released version that contains our patches.

### Running the Image Server

In order to use the advanced image viewer, you need to run IIPImage (http://iipimage.sourceforge.net) on your system.
This is how:
- install the iipsrv module by running `sh extras/install-iipsrv-osx.sh`. This will download a couple of files and compile the module for you
- once you have compiled the module you can run it via `cd extras/iipsrv-0.9.9/fcgi && ./iipsrv.fcgi --bind 127.0.0.1:7000`
- you also need to run a connector for FastCGI. Normally this happens in a web server but for convenience you can also do this without additional
installation by running `cd extras/servlet-server && java -jar start.jar`
- now you are ready to go and use the advanced image viewer

