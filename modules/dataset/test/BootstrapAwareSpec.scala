import plugins.BootstrapSource

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait BootstrapAwareSpec extends test.Specs2TestContext {

  def bootstrap = BootstrapSource.sources.find(_.spec == SAMPLE_A).getOrElse {
    throw new RuntimeException("Couldn't locate the correct sample set   to bootstrap!")
  }

  def spec = bootstrap.spec

}
