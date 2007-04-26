package davmail.exchange;

import java.io.*;
import java.util.List;

import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

public class XmlDocument {
    private Document document;

    public String getAttribute(String xpath) {
        String result = null;
        try {
            XPath xpathExpr = XPath.newInstance(xpath);
            Attribute attribute = (Attribute) xpathExpr.selectSingleNode(
                    document);
            if (attribute != null) {
                result = attribute.getValue();
            }
        } catch (JDOMException ex) {
            // TODO handle exception
        }

        return result;
    }

    public String getValue(String xpath) {
        String result = null;
        try {
            XPath xpathExpr = XPath.newInstance(xpath);
            Element element = (Element) xpathExpr.selectSingleNode(document);
            if (element != null) {
                result = element.getText();
            }
        } catch (JDOMException ex) {
            // TODO handle exception
        }

        return result;
    }

    public String getXmlValue(String xpath) {
        String result = null;
        try {
            XPath xpathExpr = XPath.newInstance(xpath);
            Element element = (Element) xpathExpr.selectSingleNode(document);
            if (element != null) {
                XMLOutputter outputter = new XMLOutputter();
                StringWriter xmlWriter = new StringWriter();
                outputter.output(element, xmlWriter);
                result = xmlWriter.toString();
            }
        } catch (IOException ex) {
            // TODO handle exception
        } catch (JDOMException ex) {
            // TODO handle exception
        }

        return result;
    }

    public List getContent(String xpath) {
        List result = null;
        XPath xpathExpr;
        try {
            xpathExpr = XPath.newInstance(xpath);
            Element element = (Element) xpathExpr.selectSingleNode(document);
            if (element != null) {
                result = element.getContent();
            }
        } catch (JDOMException ex) {
            // TODO handle exception
        }
        return result;
    }

    public List<Attribute> getNodes(String xpath) {
        List<Attribute> result = null;
        try {
            XPath xpathExpr = XPath.newInstance(xpath);
            result = xpathExpr.selectNodes(document);
        } catch (JDOMException ex) {
            // TODO handle exception
        }
        return result;
    }

    public String toString() {
        if (document == null) {
            return null;
        }
        StringWriter writer = new StringWriter();

        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        try {
            outputter.output(document, writer);
        } catch (IOException ex) {
            // TODO : handle exception
        }
        return writer.toString();
    }

    public XmlDocument() {
    }

    public void load(String location) throws JDOMException, IOException {
        document = new SAXBuilder().build(location);
    }

    public void load(InputStream stream, String dtd) throws JDOMException, IOException {
        document = new SAXBuilder().build(stream, dtd);
    }

    public void load(Document value) {
        document = value;
    }

    public String toString(Element element) {
        if (document == null) {
            return null;
        }
        StringWriter writer = new StringWriter();

        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        try {
            outputter.output(element, writer);
        } catch (IOException ex) {
            // TODO: handle Exception
        }
        return writer.toString();
    }

}
