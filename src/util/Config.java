package util;
import java.util.List;

/**
 * Holds configuration for client and server.
 * */
public class Config {
    public static String ip = "localhost";
//    public static String ip = "192.168.1.5"; // home wifi
    public static int port = 44444;
    public static String credPath = "src/Server/Authorization/users_credentials.json";
    public static String databaseStoragePath = "src/server/DatabaseStorage";

    public static List<String> reservedKeywords = List.of(new String[]{
            "TABLE", "DATABASE", "DELETE", "DROP", "SELECT", "*", "FROM", "INSERT", "ALTER"
    });

}
