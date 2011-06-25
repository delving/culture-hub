/*
 * Copyright 2007 EDL FOUNDATION
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

package eu.europeana.sip.core;

import eu.delving.metadata.MetadataNamespace;
import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import groovy.xml.MarkupBuilder;
import groovy.xml.NamespaceBuilder;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class takes code, a record, and produces a record, using the code
 * as the mapping.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MappingRunner {
    private GroovyCodeResource groovyCodeResource;
    private String code;
    private Script script;

    public MappingRunner(GroovyCodeResource groovyCodeResource, String code) {
        this.groovyCodeResource = groovyCodeResource;
        this.code = code;
    }

    public String runMapping(MetadataRecord metadataRecord) throws MappingException, DiscardRecordException {
        if (metadataRecord == null) {
            throw new RuntimeException("Null input metadata record");
        }
        try {
            Binding binding = new Binding();
            StringWriter writer = new StringWriter();
            MarkupBuilder builder = new MarkupBuilder(writer);
            NamespaceBuilder xmlns = new NamespaceBuilder(builder);
            binding.setVariable("output", builder);
            for (MetadataNamespace ns : MetadataNamespace.values()) {
                binding.setVariable(ns.getPrefix(), xmlns.namespace(ns.getUri(), ns.getPrefix()));
            }
            binding.setVariable("input", metadataRecord.getRootNode());
            if (script == null) {
                script = groovyCodeResource.createShell().parse(code);
            }
            script.setBinding(binding);
            script.run();
            return writer.toString();
        }
        catch (DiscardRecordException e) {
            throw e;
        }
        catch (MissingPropertyException e) {
            throw new MappingException(metadataRecord, "Missing Property " + e.getProperty(), e);
        }
        catch (MultipleCompilationErrorsException e) {
            StringBuilder out = new StringBuilder();
            for (Object o : e.getErrorCollector().getErrors()) {
                SyntaxErrorMessage message = (SyntaxErrorMessage) o;
                @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) SyntaxException se = message.getCause();
                // line numbers will not match
                out.append(String.format("Problem: %s\n", se.getOriginalMessage()));
            }
            throw new MappingException(metadataRecord, out.toString(), e);
        }
        catch (Exception e) {
            String codeLines = fetchCodeLines(e);
            if (codeLines != null) {
                throw new MappingException(metadataRecord, "Script Exception:\n"+codeLines, e);
            }
            else {
                throw new MappingException(metadataRecord, "Unexpected: " + e.toString(), e);
            }
        }
    }

    // a dirty hack which parses the exception's stack trace.  any better strategy welcome, but it works.
    private String fetchCodeLines(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        e.printStackTrace(out);
        String trace = sw.toString();
        Pattern pattern = Pattern.compile("Script1.groovy:([0-9]*)");
        Matcher matcher = pattern.matcher(trace);
        if (matcher.find()) {
            StringBuilder sb = new StringBuilder();
            int lineNumber = Integer.parseInt(matcher.group(1));
            for (String line : code.split("\n")) {
                lineNumber--;
                if (Math.abs(lineNumber) <= 2) {
                    sb.append(lineNumber == 0 ? ">>>" : "   ");
                    sb.append(line).append('\n');
                }
            }
            sb.append("----------- What happened ------------\n");
            sb.append(e.toString());
            return sb.toString();
        }
        return null;
    }
}
