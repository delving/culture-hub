package extensions;

import play.mvc.Http;
import play.templates.JavaExtensions;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class ViewExtensions extends JavaExtensions {

    public static String isCurrent(String action) {
        return Http.Request.current().action.contains(action) ? "current" : "";
    }

    public static String pluralize(String singular) {
        return !singular.endsWith("y") ? singular + "s" : singular.substring(0, singular.length() - 1) + "ies";
    }

    public static String shorten(String source, Integer length) {
         return source.length() > length ? source.substring(0, length) + "..." : source;
    }




}


