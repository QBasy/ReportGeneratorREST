package kz.gis.reportgenerator.app;

import net.sf.jasperreports.engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Component
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    public static void generateReportToPDF(String inputFile) {
        try (Connection conn = PostgresSQLDB.connect()) {
            String fontName = "Times New Roman";
            boolean isBold = true;
            String fontSize = "14";
            JasperPrint jasperPrint = compileAndFillReport(inputFile, fontName, fontSize, isBold, conn);

            compileReport(inputFile);
            String outputPath = PropertiesReader.getProperties("exportPath") + inputFile + "report.pdf";
            JasperExportManager.exportReportToPdfFile(jasperPrint, outputPath);

            log.info("PDF Report generated successfully!");
        } catch (SQLException | JRException e) {
            log.info("Error generating PDF report: {}", e.getMessage());
        }
    }

    private static String compileReport(String inputFile) throws JRException {
        String destination = PropertiesReader.getProperties("importPath") + inputFile;
        String compiledReportPath = destination.replace(".jrxml", ".jasper");
        JasperCompileManager.compileReportToFile(destination, compiledReportPath);

        return compiledReportPath;
    }

    public static void generateReportToHTML(String inputFile) {
        try {
            String destination = PropertiesReader.getProperties("importPath") + inputFile;

            JasperExportManager.exportReportToHtmlFile(destination, PropertiesReader.getProperties("exportPath") + inputFile + "report.xml");
            log.info("Success!!!");
        } catch (JRException e) {
            throw new RuntimeException(e);
        }
    }

    public static void generateReportToXML(String inputFile) {
        try {
            String destination = PropertiesReader.getProperties("importPath") + inputFile;

            JasperExportManager.exportReportToXmlFile(destination, PropertiesReader.getProperties("exportPath") + inputFile + "report.xml", false);
            log.info("Success!!!");
        } catch (JRException e) {
            throw new RuntimeException(e);
        }
    }

    private static JasperPrint compileAndFillReport(String inputFile, String fontName, String fontSize, boolean isBold, Connection conn) throws JRException {
        String compiledReportPath = compileReport(inputFile);
        Map<String, Object> parameters = new HashMap<>();

        if (fontName != null && fontSize != null) {
            parameters.put("fontName", fontName);
            parameters.put("fontSize", fontSize);
            parameters.put("isBold", isBold);
        }

        return JasperFillManager.fillReport(compiledReportPath, parameters, conn);
    }
}