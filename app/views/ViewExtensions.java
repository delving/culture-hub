package views;

import play.templates.JavaExtensions;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class ViewExtensions extends JavaExtensions {

    public static String pluralize(String singular) {
        return !singular.endsWith("y") ? singular + "s" : singular.substring(0, singular.length() - 1) + "ies";
    }

}
