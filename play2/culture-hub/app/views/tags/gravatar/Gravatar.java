package views.tags.gravatar;

import play.templates.FastTags;
import play.templates.GroovyTemplate;
import play.exceptions.TagInternalException;

import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;

import groovy.lang.Closure;
import play.templates.exceptions.TemplateExecutionException;
import util.MissingLibs;

/**
 *
 * @author Matteo Barbieri <barbieri.matteo@gmail.com>
 *
 */
@FastTags.Namespace("gravatar")
public class Gravatar extends FastTags {

    private static final String GRAVATAR = "http://www.gravatar.com/";
    private static final String GRAVATAR_SSL = "https://secure.gravatar.com/";


    public static void _img (Map<?, ?> args, Closure body, PrintWriter out,
                             GroovyTemplate.ExecutableTemplate template, int fromLine) {
        out.print("<img src=\"");
        _url(args,body,out,template,fromLine);
        out.print("\"");
        if (args.containsKey("size")) {
          out.print(" width=\""+args.get("size")+"\"");
          out.print(" height=\""+args.get("size")+"\"");
        }
        String alt = "";
        if (args.containsKey("alt")) {
          alt = args.get("alt").toString();
        }
        out.print(" alt=\""+alt+"\" />");
    }

    public static void _url (Map<?, ?> args, Closure body, PrintWriter out,
                             GroovyTemplate.ExecutableTemplate template, int fromLine) {

        if (!args.containsKey("arg") || args.get("arg") == null) {
            throw new TemplateExecutionException(template.template, fromLine, "Specify an e-mail address", new TagInternalException("Specify an e-mail address"));
        }

        StringBuffer url = new StringBuffer();
        if(args.containsKey("secure") && args.get("secure") == Boolean.TRUE) {
            url.append(GRAVATAR_SSL);
            args.remove("secure");
        }
        else
            url.append(GRAVATAR);

        String email = ((String) args.get("arg")).toLowerCase().trim();
        url.append("avatar/");
        url.append(MissingLibs.hexMD5(email));
        args.remove("arg");


        if(!args.isEmpty()) {
            List<String> params = new ArrayList<String>();

            if(args.containsKey("size")) {
                params.add("s="+args.get("size"));
            }

            if(args.containsKey("default")) {
                params.add("d="+args.get("default"));
            }

            if(args.containsKey("rating")) {
                params.add("r="+args.get("rating"));
            }

            url.append("?");

            Iterator i = params.iterator();
            while(i.hasNext()) {
                url.append(i.next());
                if(i.hasNext())
                    url.append("&");
            }
        }

        out.print(url.toString());

    }

}
