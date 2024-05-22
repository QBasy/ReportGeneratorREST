package kz.gis.reportgenerator.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DatabaseToXML {

    private static final Logger log = LoggerFactory.getLogger(DatabaseToXML.class);

    public static void getXML(String tableName, String[] columnsName, String customQuery) {
        try (Connection connection = PostgresSQLDB.connect()) {
            Map<String, String> columns = getTableStructure(connection, tableName, columnsName);
            generateXML(columns, tableName, customQuery, columnsName);
            log.info("File saved!");
        } catch (SQLException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getXML(String tableName, String[] columnsName) {
        try (Connection connection = PostgresSQLDB.connect()) {
            Map<String, String> columns = getTableStructure(connection, tableName, columnsName);
            String columnsNames = String.join(", ", columnsName);
            String query = "SELECT " + columnsNames + " FROM " + tableName;
            generateXML(columns, tableName, query, columnsName);
        } catch (SQLException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> getTableStructure(Connection connection, String tableName, String[] columnsName) throws SQLException {
        Map<String, String> columns = new HashMap<>();
        String query = "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '" + tableName + "';";
        log.info(query);
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            try (ResultSet resultSet = stmt.executeQuery()) {
                while (resultSet.next()) {
                    String columnName = resultSet.getString("column_name");
                    String dataType = resultSet.getString("data_type");
                    if (columnsName == null || columnsName.length == 0 || containsIgnoreCase(columnsName, columnName)) {
                        columns.put(columnName, mapDataType(dataType));
                    }
                }
            }
        }

        return columns;
    }

    private static boolean containsIgnoreCase(String[] array, String str) {
        for (String s : array) {
            if (s.equalsIgnoreCase(str)) {
                return true;
            }
        }
        return false;
    }

    private static String mapDataType(String dataType) {
        return switch (dataType) {
            case "character varying", "varchar", "text" -> "java.lang.String";
            case "integer", "bigint" -> "java.lang.Integer";
            case "boolean" -> "java.lang.Boolean";
            default -> "java.lang.Object";
        };
    }

    private static void generateXML(Map<String, String> columns, String tableName, String customQuery, String[] columnsName) throws ParserConfigurationException {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("jasperReport");
            doc.appendChild(rootElement);
            rootElement.setAttribute("xmlns", "http://jasperreports.sourceforge.net/jasperreports");
            rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            rootElement.setAttribute("xsi:schemaLocation", "http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd");
            rootElement.setAttribute("name", tableName + "_report");
            rootElement.setAttribute("language", "groovy");

            Map<String, String> attributes = Map.of(
                    "pageWidth", "595",
                    "pageHeight", "842",
                    "columnWidth", "555",
                    "leftMargin", "20",
                    "rightMargin", "20",
                    "topMargin", "20",
                    "bottomMargin", "20",
                    "uuid", UUID.randomUUID().toString()
            );
            attributes.forEach(rootElement::setAttribute);

            Element queryString = doc.createElement("queryString");
            queryString.appendChild(doc.createCDATASection(customQuery));
            rootElement.appendChild(queryString);

            for (String columnName : columns.keySet()) {
                String type = columns.get(columnName);
                Element field = doc.createElement("field");
                field.setAttribute("name", columnName);
                field.setAttribute("class", type);
                rootElement.appendChild(field);
            }

            Element title = doc.createElement("title");
            Element titleBand = doc.createElement("band");
            titleBand.setAttribute("height", "60");
            titleBand.setAttribute("splitType", "Stretch");
            Element staticTextTitle = doc.createElement("staticText");
            Element reportElementTitle = doc.createElement("reportElement");
            reportElementTitle.setAttribute("x", "140");
            reportElementTitle.setAttribute("y", "0");
            reportElementTitle.setAttribute("width", "290");
            reportElementTitle.setAttribute("height", "20");
            reportElementTitle.setAttribute("uuid", UUID.randomUUID().toString());
            staticTextTitle.appendChild(reportElementTitle);

            Element textElementTitle = createTitleTextElement(doc, "Times New Roman", "14", "true");
            staticTextTitle.appendChild(textElementTitle);
            textElementTitle.setAttribute("textAlignment", "Center");
            staticTextTitle.appendChild(textElementTitle);

            Element textTitle = doc.createElement("text");
            textTitle.appendChild(doc.createCDATASection(tableName.toUpperCase()));
            staticTextTitle.appendChild(textTitle);
            titleBand.appendChild(staticTextTitle);
            title.appendChild(titleBand);
            rootElement.appendChild(title);

            int numColumns = columns.size();
            int totalWidth = 555;
            int columnWidth = totalWidth / numColumns;
            int startX = (565 - (columnWidth * numColumns)) / 2;

            Element columnHeader = doc.createElement("columnHeader");
            Element columnBand = doc.createElement("band");
            columnBand.setAttribute("height", "30");
            addColumnHeader(doc, columnBand, columns, startX, columnWidth);
            columnHeader.appendChild(columnBand);
            rootElement.appendChild(columnHeader);

            Element detail = doc.createElement("detail");
            Element detailBand = doc.createElement("band");
            detailBand.setAttribute("height", "30");
            addDetailSection(doc, detailBand, columns, startX, columnWidth);
            detail.appendChild(detailBand);
            rootElement.appendChild(detail);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(PropertiesReader.getProperties("importPath") + tableName + "_report.jrxml"));
            transformer.transform(source, result);

        } catch (TransformerException e) {
            log.info(e.getMessage());
        }
    }

    private static void addColumnHeader(Document doc, Element band, Map<String, String> columns, int startX, int columnWidth) {
        int x = startX;
        for (String columnName : columns.keySet()) {
            Element staticText = doc.createElement("staticText");
            Element reportElement = doc.createElement("reportElement");
            reportElement.setAttribute("x", String.valueOf(x));
            reportElement.setAttribute("y", "0");
            reportElement.setAttribute("width", String.valueOf(columnWidth));
            reportElement.setAttribute("height", "30");
            staticText.appendChild(reportElement);

            Element box = createBox(doc);
            staticText.appendChild(box);

            Element textElement = createTextElement(doc);
            Element fontTitle = doc.createElement("font");
            fontTitle.setAttribute("fontName", "Times New Roman");
            fontTitle.setAttribute("is_bold", "true");
            staticText.appendChild(textElement);

            Element text = doc.createElement("text");
            text.appendChild(doc.createCDATASection(columnName.toUpperCase()));
            staticText.appendChild(text);

            band.appendChild(staticText);
            x += columnWidth;
        }
    }

    private static Element createTitleTextElement(Document doc, String fontName, String fontSize, String isBold) {
        Element textElement = doc.createElement("textElement");
        textElement.setAttribute("textAlignment", "Center");

        Element font = doc.createElement("font");
        font.setAttribute("fontName", fontName);
        font.setAttribute("size", fontSize);
        font.setAttribute("isBold", isBold);
        textElement.appendChild(font);

        return textElement;
    }

    private static void addDetailSection(Document doc, Element band, Map<String, String> columns, int startX, int columnWidth) {
        int x = startX;
        for (String columnName : columns.keySet()) {
            Element textField = doc.createElement("textField");
            Element reportElement = doc.createElement("reportElement");
            reportElement.setAttribute("x", String.valueOf(x));
            reportElement.setAttribute("y", "0");
            reportElement.setAttribute("width", String.valueOf(columnWidth));
            reportElement.setAttribute("height", "30");
            textField.appendChild(reportElement);

            Element box = createBox(doc);
            textField.appendChild(box);

            Element textElement = createTextElement(doc);
            textField.appendChild(textElement);

            Element textFieldExpression = doc.createElement("textFieldExpression");
            textFieldExpression.appendChild(doc.createCDATASection("$F{" + columnName + "}"));
            textField.appendChild(textFieldExpression);

            band.appendChild(textField);
            x += columnWidth;
        }
    }

    private static Element createBox(Document doc) {
        Element box = doc.createElement("box");
        box.setAttribute("topPadding", "0");
        box.setAttribute("leftPadding", "0");
        box.setAttribute("bottomPadding", "0");
        box.setAttribute("rightPadding", "0");

        Element topPen = doc.createElement("topPen");
        topPen.setAttribute("lineWidth", "1.0");
        box.appendChild(topPen);

        Element leftPen = doc.createElement("leftPen");
        leftPen.setAttribute("lineWidth", "1.0");
        box.appendChild(leftPen);

        Element bottomPen = doc.createElement("bottomPen");
        bottomPen.setAttribute("lineWidth", "1.0");
        box.appendChild(bottomPen);

        Element rightPen = doc.createElement("rightPen");
        rightPen.setAttribute("lineWidth", "1.0");
        box.appendChild(rightPen);

        return box;
    }

    private static Element createTextElement(Document doc) {
        Element textElement = doc.createElement("textElement");
        textElement.setAttribute("textAlignment", "Center");

        Element font = doc.createElement("font");
        font.setAttribute("fontName", "Times New Roman");
        font.setAttribute("size", "10");
        font.setAttribute("isBold", "true");
        textElement.appendChild(font);

        Element paragraph = doc.createElement("paragraph");
        paragraph.setAttribute("lineSpacing", "Single");
        textElement.appendChild(paragraph);

        return textElement;
    }
}
