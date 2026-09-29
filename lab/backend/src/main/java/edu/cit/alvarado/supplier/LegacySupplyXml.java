package edu.cit.alvarado.supplier;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Deliberately dependency-free: builds and parses XML with only
 * javax.xml.parsers / javax.xml.transform, both part of the JDK. This
 * avoids adding jackson-dataformat-xml or any other new pom.xml entry just
 * for a handful of small, fixed-shape documents.
 */
final class LegacySupplyXml {

    private LegacySupplyXml() {
    }

    // ---------- building request documents ----------

    static String buildAuthRequest(String clientId, String apiKey) {
        Document doc = newDocument();
        Element root = doc.createElement("AuthRequest");
        doc.appendChild(root);
        appendText(doc, root, "ClientId", clientId);
        appendText(doc, root, "ApiKey", apiKey);
        return serialize(doc);
    }

    static String buildPurchaseOrder(String supplierSku, int qty, String buyerRef) {
        Document doc = newDocument();
        Element root = doc.createElement("PurchaseOrder");
        doc.appendChild(root);
        appendText(doc, root, "SupplierSku", supplierSku);
        appendText(doc, root, "Qty", String.valueOf(qty));
        appendText(doc, root, "BuyerRef", buyerRef);
        return serialize(doc);
    }

    // ---------- parsing response documents ----------

    record AuthResult(String sessionToken, String issuedAt) {
    }

    static AuthResult parseAuthResponse(String xml) {
        Element root = parseRoot(xml, "AuthResponse");
        return new AuthResult(textOf(root, "SessionToken"), textOf(root, "IssuedAt"));
    }

    record PurchaseOrderAck(String poNumber, int statusCode, String supplierSku,
                             int qty, String uom, String buyerRef, String createdAt) {
    }

    static PurchaseOrderAck parsePurchaseOrderAck(String xml) {
        // Accepts either the creation ack or the status document - both
        // share the same fields per the manual, plus CheckedAt on status
        // reads (ignored here, callers that need it read it separately).
        Element root = parseAnyRoot(xml);
        return new PurchaseOrderAck(
                textOf(root, "PoNumber"),
                Integer.parseInt(textOf(root, "StatusCode")),
                textOf(root, "SupplierSku"),
                Integer.parseInt(textOf(root, "Qty")),
                textOf(root, "Uom"),
                textOf(root, "BuyerRef"),
                textOf(root, "CreatedAt"));
    }

    record LsError(String code, String message) {
    }

    static LsError parseLsError(String xml) {
        Element root = parseRoot(xml, "LSError");
        return new LsError(textOf(root, "Code"), textOf(root, "Message"));
    }

    /** True if this response body looks like an LSError document at all -
     *  used by the client to decide how to parse an unexpected-status body. */
    static boolean looksLikeError(String xml) {
        return xml != null && xml.contains("<LSError>");
    }

    // ---------- low-level helpers ----------

    private static Document newDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.newDocument();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create XML document", e);
        }
    }

    private static void appendText(Document doc, Element parent, String tag, String value) {
        Element el = doc.createElement(tag);
        el.setTextContent(value);
        parent.appendChild(el);
    }

    private static String serialize(Document doc) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize XML document", e);
        }
    }

    private static Element parseRoot(String xml, String expectedRootTag) {
        Element root = parseAnyRoot(xml);
        if (!expectedRootTag.equals(root.getTagName())) {
            throw new IllegalStateException("Expected <" + expectedRootTag + "> but got <" + root.getTagName() + ">");
        }
        return root;
    }

    private static Element parseAnyRoot(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse XML response: " + xml, e);
        }
    }

    private static String textOf(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }
}
