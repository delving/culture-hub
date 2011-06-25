package eu.delving.metadata;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class describes how a field is mapped.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("field-mapping")
public class FieldMapping implements Comparable<FieldMapping> {

    @XStreamAlias("dictionary")
    public Map<String, String> dictionary;

    @XStreamAlias("groovy-code")
    public List<String> code;

    @XStreamOmitField
    public FieldDefinition fieldDefinition;

    @XStreamOmitField
    public List<String> variables;

    public FieldMapping(FieldDefinition fieldDefinition) {
        this.fieldDefinition = fieldDefinition;
    }

    public FieldDefinition getDefinition() {
        if (fieldDefinition == null) {
            throw new IllegalStateException("Expected that FieldMapping has fieldDefinition");
        }
        return fieldDefinition;
    }

    public String getFieldNameString() {
        return getDefinition().getFieldNameString();
    }

    public void clearCode() {
        code = null;
    }

    public void addCodeLine(String line) {
        if (code == null) {
            code = new ArrayList<String>();
        }
        code.add(line.trim());
        variables = null;
    }

    public void createDictionary(Set<String> domainValues) {
        this.dictionary = new TreeMap<String,String>();
        for (String key : domainValues) {
            this.dictionary.put(key,"");
        }
    }

    public List<String> getVariableNames() {
        if (variables == null) {
            variables = new ArrayList<String>();
            for (String line : code) {
                Matcher matcher = VARIABLE_PATTERN.matcher(line);
                while (matcher.find()) {
                    String var = matcher.group(0);
                    if (var.endsWith("(")) {
                        var = var.substring(0, var.lastIndexOf('.'));
                    }
                    variables.add(var);
                }
            }
        }
        return variables;
    }

    public boolean codeLooksLike(String codeString) {
        Iterator<String> walk = code.iterator();
        for (String line : codeString.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                if (!walk.hasNext()) {
                    return false;
                }
                String codeLine = walk.next();
                if (!codeLine.equals(line)) {
                    return false;
                }
            }
        }
        return !walk.hasNext();
    }
    
    public void setCode(String code) {
        if (this.code == null) {
            this.code = new ArrayList<String>();
        }
        this.code.clear();
        for (String line : code.split("\n")) {
            this.code.add(line.trim());
        }
        this.variables = null;
    }

    public String getDescription() {
        String fieldName = fieldDefinition == null ? "?" : fieldDefinition.getFieldNameString();
        if (getVariableNames().isEmpty()) {
            return fieldName;
        }
        else {
            return fieldName + " from " + getVariableNames();
        }
    }

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("input(\\.\\w+)+[\\(]?"); // todo: doesn't catch facts

    @Override
    public int compareTo(FieldMapping fieldMapping) {
        return getDefinition().compareTo(fieldMapping.getDefinition());
    }
}

