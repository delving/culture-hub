package util;

import javax.xml.stream.XMLInputFactory;

import org.codehaus.stax2.XMLInputFactory2;

/**
 * Factory class necessary due to Java's (oftentimes criticized) ability to inherit static methods vs. Scala's inability to do so
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class StaxFactory {

    public static XMLInputFactory2 newInstance() {
        return (XMLInputFactory2) XMLInputFactory.newInstance();
    }
}
