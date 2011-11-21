# Delving Culture-Hub

## Getting started

### Installing system dependencies

#### MAC OS X

    brew install mongodb
    brew install graphicsmagick

### One-time set-up

- run `sh setup-play.sh` in order to install play and culture-hub. Make sure you add the play directory to your shell path after installation so it can be found in further steps, e.g. by adding the line `export PATH=$PATH:/Users/foo/workspace/play` to your `~/.bash_profile`
- later, you may need to run `play deps --sync` by hand in order to download and install additional dependencies.
- copy `conf/production.conf.template` to `conf/production.conf`. You can ignore this file as long as you do not want to deploy the hub.
- configure the settings in `conf/application.conf`, i.e. create custom entries when you need them (e.g. `%manu.image.graphicsmagic.cmd=/opt/local/bin/gm`)
- in order to use the project in IDEA (until there will be plugin support for it), run `play idealize` to generate a module, then create a new project (without module) and import the generated module
- configure the subdomains for testing in your `/etc/hosts` file by adding e.g.:

    127.0.0.1       friesmuseum.localhost

### Running the application - development mode

Run the application via

    play run --%youruser

where `youruser` is the key you used for your custom properties in `conf/application.conf`

An example user is `bob@gmail.com` with password `secret`

### Running the application - test mode

Run the test mode via

    play test -Duser=youruser

Then you can access the test dashboard on `http://localhost:9000/@tests`

Run in prod mode via

    play start --%prod

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