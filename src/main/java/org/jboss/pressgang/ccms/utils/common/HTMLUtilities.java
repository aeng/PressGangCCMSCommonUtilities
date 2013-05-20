package org.jboss.pressgang.ccms.utils.common;

import java.io.File;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * A collection of static methods for manipulating HTML documents.
 *
 * @author Matthew Casperson
 */
public class HTMLUtilities {
    private static final Logger LOG = LoggerFactory.getLogger(HTMLUtilities.class);

    /**
     * A regular expression that identifies the start of a CSS import statement
     */
    private static final String CSS_IMPORT_START = "@import url(\"";
    /**
     * A regular expression that identifies the end of a CSS import statement
     */
    private static final String CSS_IMPORT_END = "\");";

    /**
     * Takes a XHTML file (usually generated by Publican), and inlines all the
     * CSS, image and SVG data. The resulting string is a stand alone HTML file
     * with no references to external resources.
     *
     * @param html     The original XHTML code
     * @param basePath The path on which all the resources referenced by the
     *                 XHTML file can be found
     * @return A single, stand alone version of the XHTML
     */
    public static String inlineHtmlPage(final String html, final String basePath) {
        String retValue = html;

        Document doc = null;
        try {
            doc = XMLUtilities.convertStringToDocument(html);
        } catch (Exception ex) {
            LOG.error("Failed to convert the HTML into a DOM Document", ex);
        }
        if (doc != null) {
            inlineImgNodes(doc, basePath);
            inlineCssNodes(doc, basePath);
            inlineSvgNodes(doc, basePath);

            retValue = XMLUtilities.convertDocumentToString(doc);
        }

        return retValue;
    }

    /**
     * Finds any reference to an external SVG image and replaces the node with
     * the SVG inline data.
     *
     * @param doc      The document that holds the XHTML
     * @param basePath The base path where the SVG images can be found
     */
    private static void inlineSvgNodes(final Document doc, final String basePath) {
        try {
            // handle null inputs
            if (doc == null) return;

            final String fixedBasePath = basePath == null ? "" : basePath;

            final List<Node> nodes = XMLUtilities.getChildNodes(doc.getDocumentElement(), "object");
            for (final Node node : nodes) {
                final NamedNodeMap attributes = node.getAttributes();

                final Node typeAttribute = attributes.getNamedItem("type");
                final Node dataAttribute = attributes.getNamedItem("data");

                if (typeAttribute != null && typeAttribute.getTextContent().equals("image/svg+xml") && dataAttribute != null) {
                    final String data = dataAttribute.getTextContent();
                    final File dataFile = new File(fixedBasePath + "/" + data);
                    if (dataFile.exists()) {
                        final String fileString = FileUtilities.readFileContents(dataFile);
                        final Document svgDoc = XMLUtilities.convertStringToDocument(fileString);

                        if (svgDoc != null) {
                            final Element svgDocElement = svgDoc.getDocumentElement();
                            if (svgDocElement != null && svgDocElement.getNodeName().equals("svg")) {
                                final Node parent = node.getParentNode();
                                if (parent != null) {
                                    final Node importedNode = doc.importNode(svgDocElement, true);
                                    parent.replaceChild(importedNode, node);
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception ex) {
            LOG.error("Unable to convert external SVG image to DOM Document", ex);
        }
    }

    /**
     * Finds any reference to an external CSS scripts and replaces the node with
     * inline CSS data.
     *
     * @param doc      The document that holds the XHTML
     * @param basePath The base path where the CSS scripts can be found
     */
    private static void inlineCssNodes(final Document doc, final String basePath) {
        if (doc == null) return;

        final String fixedBasePath = basePath == null ? "" : basePath;

        final List<Node> nodes = XMLUtilities.getChildNodes(doc.getDocumentElement(), "link");
        for (final Node node : nodes) {
            final NamedNodeMap attributes = node.getAttributes();

            final Node relAttribute = attributes.getNamedItem("rel");
            final Node hrefAttribute = attributes.getNamedItem("href");
            final Node mediaAttribute = attributes.getNamedItem("media");

            if (relAttribute != null && relAttribute.getTextContent().equals("stylesheet") && hrefAttribute != null) {
                final String href = hrefAttribute.getTextContent();
                final File hrefFile = new File(fixedBasePath + "/" + href);
                if (hrefFile.exists()) {
                    // find the base path for any import statements that
                    // might be in the css file
                    String cssBasePath = "";
                    int end = href.lastIndexOf("/");
                    if (end == -1) end = href.lastIndexOf("\\");
                    if (end != -1) cssBasePath = href.substring(0, end);

                    final String fileString = inlineCssImports(FileUtilities.readFileContents(hrefFile), basePath + "/" + cssBasePath);

                    final Node parent = node.getParentNode();
                    if (parent != null) {
                        final Element newNode = doc.createElement("style");
                        newNode.setAttribute("type", "text/css");

                        if (mediaAttribute != null) newNode.setAttribute("media", mediaAttribute.getTextContent());

                        newNode.setTextContent(fileString);

                        parent.replaceChild(newNode, node);
                    }
                }
            }
        }
    }

    /**
     * Finds any reference to an external CSS scripts that themselves have been
     * referenced in a CSS script using an import statement and replaces the
     * node with inline CSS data.
     *
     * @param doc      The document that holds the XHTML
     * @param basePath The base path where the CSS scripts can be found
     */
    private static String inlineCssImports(final String css, final String basePath) {
        String retValue = css;
        int start;
        while ((start = retValue.indexOf(CSS_IMPORT_START)) != -1) {
            int end = retValue.indexOf(CSS_IMPORT_END, start);
            if (end != -1) {
                final String filePath = retValue.substring(start + CSS_IMPORT_START.length(), end);
                final String fileData = FileUtilities.readFileContents(new File(basePath + "/" + filePath));

                retValue = retValue.replace(CSS_IMPORT_START + filePath + CSS_IMPORT_END, fileData);
            }
        }
        return retValue;
    }

    /**
     * Finds any reference to an external images and replaces them with inline
     * base64 data
     *
     * @param doc      The document that holds the XHTML
     * @param basePath The base path where the images can be found
     */
    private static void inlineImgNodes(final Document doc, final String basePath) {
        // handle null inputs
        if (doc == null) return;

        final String fixedBasePath = basePath == null ? "" : basePath;

        final List<Node> imageNodes = XMLUtilities.getChildNodes(doc.getDocumentElement(), "img");
        for (final Node node : imageNodes) {
            final NamedNodeMap attributes = node.getAttributes();
            final Node srcAttribute = attributes.getNamedItem("src");
            if (srcAttribute != null) {
                final String src = srcAttribute.getTextContent();
                final File srcFile = new File(fixedBasePath + "/" + src);
                if (srcFile.exists()) {
                    final byte[] srcFileByteArray = FileUtilities.readFileContentsAsByteArray(srcFile);
                    if (srcFileByteArray != null) {
                        // get the file extension of the original image
                        final int extensionStringLocation = src.lastIndexOf(".");

                        // make sure we have an extension
                        if (extensionStringLocation != -1 && extensionStringLocation != src.length() - 1) {
                            final String extension = src.substring(extensionStringLocation + 1);

                            // encode the image
                            final byte[] srcFileEncodedByteArray = Base64.encodeBase64(srcFileByteArray);
                            final String srcFileEncodedString = new String(srcFileEncodedByteArray);

                            final Node parent = node.getParentNode();
                            if (parent != null) {
                                final Element newImgNode = doc.createElement("img");
                                newImgNode.setAttribute("src", "data:image/" + extension + ";base64," + srcFileEncodedString);

                                parent.replaceChild(newImgNode, node);
                            }
                        }
                    }
                }
            }
        }
    }
}
