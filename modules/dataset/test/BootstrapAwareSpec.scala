import plugins.BootstrapSource

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait BootstrapAwareSpec extends Specs2TestContext {

  def bootstrap = BootstrapSource.sources.headOption.getOrElse {
    throw new RuntimeException("Couldn't locate any sets to bootstrap!")
  }

  def spec = bootstrap.spec

}
