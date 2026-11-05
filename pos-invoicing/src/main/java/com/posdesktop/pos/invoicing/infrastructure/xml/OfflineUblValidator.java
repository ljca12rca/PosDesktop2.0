package com.posdesktop.pos.invoicing.infrastructure.xml;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/** Valida exclusivamente estructura UBL oficial; no acepta documentos ante la DIAN. */
public final class OfflineUblValidator {
    private final Schema schema;
    public record Issue(String rule, int line, int column, String description) { }
    public record Result(List<Issue> issues) {
        public Result { issues = List.copyOf(issues); }
        public boolean structurallyValid() { return issues.isEmpty(); }
    }

    public OfflineUblValidator() {
        this(false, false);
    }

    /** Perfil con correccion local explicita de coID2Type/@schemeID. Ver docs/dian19-rules.md. */
    public static OfflineUblValidator dian19() { return new OfflineUblValidator(true, true); }
    public static OfflineUblValidator dian19Published() { return new OfflineUblValidator(true, false); }

    private OfflineUblValidator(boolean dian, boolean corrected) {
        String prefix = dian ? "/dian19/xsd/" : "/ubl21/";
        String rootUri = "classpath:" + prefix;
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setResourceResolver((type, namespace, publicId, systemId, baseURI) -> {
                URI resolved = URI.create(baseURI == null ? rootUri : baseURI).resolve(systemId).normalize();
                if (!resolved.toString().startsWith(rootUri)) throw new IllegalArgumentException("Importacion XSD fuera de la lista local.");
                return new LocalInput(resolved.toString(), resource(resolved.getPath()));
            });
            String root = prefix + "maindoc/UBL-Invoice-2.1.xsd";
            String extensionName = corrected ? "DIAN_UBL_Structures-profile19.xsd" : "DIAN_UBL_Structures.xsd";
            try (InputStream input = resource(root);
                 InputStream extension = dian ? resource(prefix + "maindoc/" + extensionName) : InputStream.nullInputStream()) {
                StreamSource source = new StreamSource(input);
                source.setSystemId("classpath:" + root);
                StreamSource dianSource = new StreamSource(extension);
                dianSource.setSystemId(rootUri + "maindoc/" + extensionName);
                schema = dian ? factory.newSchema(new javax.xml.transform.Source[]{source, dianSource}) : factory.newSchema(source);
            }
        } catch (Exception error) {
            throw new IllegalStateException("No se pudieron cargar los XSD oficiales UBL locales. No se omite la validacion.", error);
        }
    }

    public Result validate(String xml) {
        List<Issue> issues = new ArrayList<>();
        try {
            var factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var reader = factory.newSAXParser().getXMLReader();
            reader.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("Entidad externa bloqueada."); });
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException e) { add(e); }
                @Override public void error(SAXParseException e) { add(e); }
                @Override public void fatalError(SAXParseException e) { add(e); }
                private void add(SAXParseException e) { issues.add(new Issue("UBL_XSD", e.getLineNumber(), e.getColumnNumber(), e.getMessage())); }
            });
            validator.validate(new SAXSource(reader, new InputSource(new StringReader(xml))));
        } catch (Exception error) {
            if (issues.isEmpty()) issues.add(new Issue("XML_SECURE_PARSE", -1, -1, "XML mal formado, inseguro o no validable."));
        }
        return new Result(issues);
    }

    private static InputStream resource(String path) {
        InputStream input = OfflineUblValidator.class.getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("Esquema local ausente: " + path);
        return input;
    }

    private static final class LocalInput implements LSInput {
        private String systemId;
        private InputStream stream;
        LocalInput(String systemId, InputStream stream) { this.systemId = systemId; this.stream = stream; }
        @Override public InputStream getByteStream() { return stream; }
        @Override public void setByteStream(InputStream stream) { this.stream = stream; }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String id) { systemId = id; }
        @Override public String getBaseURI() { return systemId; }
        @Override public String getEncoding() { return "UTF-8"; }
        @Override public Reader getCharacterStream() { return null; }
        @Override public String getStringData() { return null; }
        @Override public String getPublicId() { return null; }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setBaseURI(String value) { }
        @Override public void setEncoding(String value) { }
        @Override public void setCharacterStream(Reader value) { }
        @Override public void setStringData(String value) { }
        @Override public void setPublicId(String value) { }
        @Override public void setCertifiedText(boolean value) { }
    }
}
