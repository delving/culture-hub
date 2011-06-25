/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.metadata;

import java.io.Serializable;
import java.util.Stack;

/**
 * A path consisting of a stack of instances of QName, representing the paths
 * of elements that came from the analysis process.
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class Path implements Comparable<Path>, Serializable {
    private Stack<Tag> stack = new Stack<Tag>();
    private String string;

    public Path() {
    }

    public Path(String pathString) {
        if (!pathString.startsWith("/")) {
            throw new IllegalArgumentException("Path string must start at the root with slash: [" + pathString + "]");
        }
        for (String part : pathString.substring(1).split("/")) {
            stack.push(Tag.create(part));
        }
    }

    public Path(Path path) {
        if (path.stack != null) {
            for (Tag name : path.stack) {
                stack.push(name);
            }
        }
    }

    public Path(Path path, int count) {
        for (Tag name : path.stack) {
            if (count-- > 0) {
                stack.push(name);
            }
        }
    }

    public void push(Tag tag) {
        stack.push(tag);
        string = null;
    }

    public void pop() {
        stack.pop();
        string = null;
    }

    @Override
    public boolean equals(Object path) {
        return toString().equals(path.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public int compareTo(Path path) {
        return toString().compareTo(path.toString());
    }

    public Tag getTag(int level) {
        if (level < stack.size()) {
            return stack.get(level);
        }
        else {
            return null;
        }
    }

    public Tag peek() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    public int size() {
        return stack.size();
    }

    public String toString() {
        if (string == null) {
            StringBuilder builder = new StringBuilder(150);
            for (Tag tag : stack) {
                builder.append('/');
                builder.append(tag);
            }
            string = builder.toString();
        }
        return string;
    }
}
