package kz.gis.reportgenerator.app;

import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@Component
public class PropertiesReader {
    private static final Properties properties = new Properties();;
    private static final FileInputStream fileInputStream;

    static {
        try {
            fileInputStream = new FileInputStream("./src/main/java/etc/generate.properties");
            properties.load(fileInputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getProperties(String key) {
        return properties.getProperty(key);
    }
}