import play.Logger;
import play.PlayPlugin;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class LegacyPlugin extends PlayPlugin {

    @Override
    public void onLoad() {
        Logger.info("Starting the Delving legacy services module");
    }
}
