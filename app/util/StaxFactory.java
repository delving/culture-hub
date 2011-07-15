package util;

import javax.xml.stream.XMLInputFactory;

import org.codehaus.stax2.XMLInputFactory2;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class StaxFactory {

    public static XMLInputFactory2 newInstance() {
        return (XMLInputFactory2) XMLInputFactory.newInstance();
    }
}
