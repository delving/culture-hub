# NOTICE: no longer actively maintained

This version of the Delving CultureHub is no langer actively maintained. The core functionality is now being developed in the [delving/hub3](https://github.com/delving/hub3) repository.

# CultureHub

The CultureHub is a platform that aims at making cultural objects accessible online. More information at http://delving.eu

## Getting started

### Installing system dependencies

#### MAC OS X

    brew install mongodb
    brew install graphicsmagick

### One-time set-up

- get the Play! 2 framework at http://www.playframework.org and make sure the play script is in your environment PATH
- configure the subdomains for testing in your `/etc/hosts` file by adding e.g.:

    127.0.0.1       delving.localhost

### Running the application - development mode

Run the application via

    play run

If you want to use a custom configuration file for development, use

    play run -Dconfig.file=conf/development.conf

The `development.conf` file can include the default configuration via

    include "application.conf"

### Running the tests

Run the tests via

    play test



### Running Apache Solr

To use Apache Solr run `ant startSolr`. Use `ant stopSolr` in order to stop it.

### Running the Image Server

In order to use the advanced image viewer, you need to run IIPImage (http://iipimage.sourceforge.net) on your system.

This is how:
- install the iipsrv module by running `sh extras/install-iipsrv-osx.sh`. This will download a couple of files and compile the module for you
- once you have compiled the module you can run it via `cd extras/iipsrv-0.9.9/fcgi && ./iipsrv.fcgi --bind 127.0.0.1:7000`
- you also need to run a connector for FastCGI. Normally this happens in a web server but for convenience you can also do this without additional
installation by running `cd extras/servlet-server && java -jar start.jar`
- now you are ready to go and use the advanced image viewer

### Selenium tests on a build server

In order to run the selenium tests that use the Chrome driver, you'll need to do the following on a build-server (debian / ubuntu):

- install chromium and xvfb: `aptitude install chromium-browser xvfb`
- edit `/etc/chromium-browser/default` and set `CHROMIUM_FLAGS="--display:1"`
- for Jenkins, install the xvfb plugin and use it in your build
