package kz.gis.reportgenerator.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class PostgresSQLDB {
    private static final Logger log = LoggerFactory.getLogger(PostgresSQLDB.class);
    public static String URL = PropertiesReader.getProperties("postgresql.url");
    public static String USER = PropertiesReader.getProperties("postgresql.username");
    public static String PASSWORD = PropertiesReader.getProperties("postgresql.password");

    public static Connection connect() throws SQLException {
        log.info("Connecting to database...");
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
