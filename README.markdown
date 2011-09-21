# Delving culture-hub

Proof of concept of a services application written with play-scala.

This will eventually demonstrate things such as authentication & authorization, content-type based rendering, etc.

## Getting started

### Installing system dependencies

#### MAC OS X

    brew install mongodb
    brew install graphicsmagick
    # todo add other dependencies.


### One-time set-up

- run `sh setup-play-patched.sh` in order to install play. Make sure you add the play directory to your shell path after installation so it can be found in further steps, e.g. by adding the line `export PATH=$PATH:/Users/foo/workspace/play` to your `~/.bash_profile`
- from the `culture-hub` directory, run `sh setup.sh` in order to setup the application (find dependencies, etc.). You may need to run `play dependencies` later on by hand in order to download and install additional dependencies.
- configure the settings at the end of `conf/application.conf`, i.e. create custom entries when you need them (e.g. `%manu.image.graphicsmagic.cmd=/opt/local/bin/gm`)
- in order to use the project in IDEA (until there will be plugin support for it), run `play idealize` to generate a module, then create a new project (without module) and import the generated module
- configure the subdomains for testing in your `/etc/hosts` file by adding:

    127.0.0.1       norvegiana.localhost
    127.0.0.1       friesmuseum.localhost

### Running the application - development mode

Run the application via

    play run -Xss4m --%youruser

where `youruser` is the key you used for your custom properties in `conf/application.conf``

An example user is `bob@gmail.com` with password `secret`

### Running the application - test mode

Run the test mode via

    play test -Xss4m -Duser=youruser

Run in prod mode (for use with Sip-Creator) via

    play start -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -XX:+CMSClassUnloadingEnabled -XX:+CMSPermGenSweepingEnabled -XX:SoftRefLRUPolicyMSPerMB=1 -XX:MaxPermSize=128m -Xms512m -Xmx512m -Xss4m --%prod

Then you can access the test dashboard on `http://localhost:9000/@tests`

### Running Apache Solr

To use Apache Solr go to `extras/solr-server` and run `java -jar start.jar`.

### Running the Image Server

In order to use the advanced image viewer, you need to run IIPImage (http://iipimage.sourceforge.net) on your system.
This is how:
- install the iipsrv module by running `sh extras/install-iipsrv-osx.sh`. This will download a couple of files and compile the module for you
- once you have compiled the module you can run it via `cd extras/iipsrv-0.9.9/fcgi && ./iipsrv.fcgi --bind 127.0.0.1:7000`
- you also need to run a connector for FastCGI. Normally this happens in a web server but for convenience you can also do this without additional
installation by running `cd extras/servlet-server && java -jar start.jar`
- now you are ready to go and use the advanced image viewer

### Batch conversion of high-resolution images

In order to convert high-resoltution images in batch mode, we can use the magicktiler:

- clone it with `git clone git@github.com:delving/magicktiler.git`
- build it with `ant build:dist`
- run it with `java -jar magicktiler.jar -s ptif -i /path/to/the/images`

This will render PTIF (tiled TIF) images in the same directory than the input directory (until this is fixed in magicktiler)

## Notes

### Play installation - Play versions

As Play has some issues (just as any other frameworks) it makes sense, during development, to use the latest development version.
This way we can also use patches that did not yet make it into a release. The `setup-play-patched.sh` takes care of checking out the development branch and to apply patches for issues we may find.
For production we then use the newly released version that contains our patches.