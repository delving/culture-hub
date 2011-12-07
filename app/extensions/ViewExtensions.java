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


