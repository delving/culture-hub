# play-services

Proof of concept of a services application written with play-scala.

This will eventually demonstrate things such as authentication & authorization, content-type based rendering, etc.

## Getting started

- run `sh setup-play.sh` in order to install play. Make sure you add the play directory to your shell path after installation so it can be found in further steps.
- from the `play-services` directory, run `sh setup.sh` in order to setup the application. You may need to run `play dependencies` later on by hand in order to download and install additional dependencies.
- configure the settings at the end of `conf/application.conf`
- run the application via `play run`
- in order to use the project in IDEA (until there will be plugin support for it), run `play idealize` to generate a module, then create a new project (without module) and import the generated module
- an example user is `bob@gmail.com` with password `bob`
