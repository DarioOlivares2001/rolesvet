package com.rolesveterinaria;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public class OracleDBConnection {
    private static final Logger logger = Logger.getLogger(OracleDBConnection.class.getName());

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            boolean isValid = conn.isValid(5);
            logger.info("Conexión a la base de datos probada: " + (isValid ? "Válida" : "Inválida"));
            return isValid;
        } catch (SQLException e) {
            logger.severe("Error al probar la conexión: " + e.getMessage());
            return false;
        }
    }

    public static Connection getConnection() throws SQLException {
        String url = "jdbc:oracle:thin:@" + System.getenv("ORACLE_TNS_NAME") + "?TNS_ADMIN=" + System.getenv("ORACLE_WALLET_PATH");
        String user = System.getenv("ORACLE_USER");
        String password = System.getenv("ORACLE_PASSWORD");
        String walletLocation = System.getenv("ORACLE_WALLET_PATH");

        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("oracle.net.ssl_version", "1.2");
        props.setProperty("oracle.net.wallet_location", "(SOURCE=(METHOD=file)(METHOD_DATA=(DIRECTORY=" + walletLocation + ")))");

        logger.info("Configurando conexión a la base de datos:");
        logger.info("URL: " + url);
        logger.info("Usuario: " + user);
        logger.info("Ubicación del wallet: " + props.getProperty("oracle.net.wallet_location"));

        try {
            Connection conn = DriverManager.getConnection(url, props);
            logger.info("Conexión establecida correctamente.");
            return conn;
        } catch (SQLException e) {
            logger.severe("Error al establecer la conexión: " + e.getMessage());
            throw e;
        }
    }
}