import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String LOG_FILE = "log.txt";
    private static PrintWriter writer;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void init() {
        try {
            writer = new PrintWriter(new FileWriter(LOG_FILE, false));
        } catch (IOException e) {
            System.err.println("No se pudo inicializar el logger: " + e.getMessage());
        }
    }

    public static void info(String message) {
        if (writer != null) {
            String timestamp = LocalDateTime.now().format(formatter);
            String logLine = "[" + timestamp + "] " + message;
            writer.println(logLine);
            writer.flush(); // Ensure real-time updates are saved to disk instantly
        }
    }

    public static void close() {
        if (writer != null) {
            writer.close();
        }
    }
}
