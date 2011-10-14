package extensions;

import play.mvc.Http;
import play.templates.JavaExtensions;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class ViewExtensions extends JavaExtensions {

    public static String isCurrent(String action) {
        return Http.Request.current().action.startsWith(action) ? "current" : "";
    }

}
