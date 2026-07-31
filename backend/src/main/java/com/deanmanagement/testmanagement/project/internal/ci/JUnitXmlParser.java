package com.deanmanagement.testmanagement.project.internal.ci;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the common JUnit XML schema (Surefire, Jest, pytest, etc.) into normalized {@link CiResult}s.
 * Uses a DOM parser with DOCTYPE declarations disabled to prevent XXE.
 */
@Component
public class JUnitXmlParser {

    public List<CiResult> parse(byte[] xml) {
        Document doc = parseDocument(xml);
        NodeList testcases = doc.getElementsByTagName("testcase");
        List<CiResult> results = new ArrayList<>();
        for (int i = 0; i < testcases.getLength(); i++) {
            Element testcase = (Element) testcases.item(i);
            results.add(toResult(testcase));
        }
        if (results.isEmpty()) {
            throw new IllegalArgumentException("No <testcase> elements found in JUnit XML");
        }
        return results;
    }

    private CiResult toResult(Element testcase) {
        String name = testcase.getAttribute("name");
        String className = testcase.getAttribute("classname");
        String title = (className != null && !className.isBlank()) ? className + "." + name : name;

        String suiteName = null;
        Node parent = testcase.getParentNode();
        if (parent instanceof Element parentEl && "testsuite".equals(parentEl.getTagName())) {
            suiteName = parentEl.getAttribute("name");
        }

        Element failure = firstChild(testcase, "failure");
        Element error = firstChild(testcase, "error");
        Element skipped = firstChild(testcase, "skipped");

        TestResultStatus status;
        String message = null;
        if (failure != null) {
            status = TestResultStatus.FAILED;
            message = messageOf(failure);
        } else if (error != null) {
            status = TestResultStatus.BLOCKED;
            message = messageOf(error);
        } else if (skipped != null) {
            status = TestResultStatus.SKIPPED;
            message = messageOf(skipped);
        } else {
            status = TestResultStatus.PASSED;
        }

        return new CiResult(suiteName, title, status, message, List.of());
    }

    private String messageOf(Element el) {
        String attr = el.getAttribute("message");
        String text = el.getTextContent() != null ? el.getTextContent().trim() : "";
        if (attr != null && !attr.isBlank() && !text.isEmpty()) {
            return attr + "\n" + text;
        }
        if (attr != null && !attr.isBlank()) {
            return attr;
        }
        return text.isEmpty() ? null : text;
    }

    private Element firstChild(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    private Document parseDocument(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml));
            doc.getDocumentElement().normalize();
            return doc;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JUnit XML: " + e.getMessage());
        }
    }
}
