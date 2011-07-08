### Initial data provisioning via YAML

We use YAML in order to provide initial data (for empty instances). YAML is much more readable than e.g. XML and allows for easy creation and maintenance
of test data.

In order to easily work with objects stored in mongo, we make use of Salat [https://github.com/novus/salat](Salat).
Salat allows it to save case classes to mongo with very little effort, using a `SalatDAO`.

In this way, we can use one and the same model definition (a case class, see the contents of the `models` package) for all data handling.

Loading data from a Yaml file then looks like this:

    // load from YAML
    val themes = YamlLoader.load[List[PortalTheme]]("some-yaml-file.yml"))

    // save to mongo
    themes foreach {
      PortalTheme.insert(_)
    }

See e.g. `util.ThemeHandlerComponent` for a more detailed example.

There are some restrictions that apply, however:

- when a value is missing from a mongo db object that is required by the constructor of the case class, construction will fail,
unless marking that field as optional. This can be done by using an Option and providing a default (see `models.PortalTheme` for an example

- not all collections are currently supported (see https://github.com/novus/salat/wiki/Collections), but what is there should fit us

In order to achieve interoperability between the YAML loading mechanism (based on [http://code.google.com/p/snakeyaml/](SnakeYAML)) and Salat,
we use our own `util.YamlLoader` which takes care of performing the necessary conversions for loading the YAML into "Salatized" case classes.

### Dependency Injection using the Cake Pattern

TODO