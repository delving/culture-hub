/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
