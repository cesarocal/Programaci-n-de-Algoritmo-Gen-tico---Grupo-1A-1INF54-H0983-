import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger unificado para ambos algoritmos (AG y ACS).
 * Escribe en un archivo de log sin saturar la terminal.
 */
public class Logger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter writer = null;
    private static String logFile = "log_simulacion.txt";

    public static void init(String nombreArchivo) {
        logFile = nombreArchivo;
        try {
            writer = new PrintWriter(new FileWriter(logFile, false));
            info("=== LOGGER INICIALIZADO ===");
        } catch (IOException e) {
            System.err.println("[Logger] No se pudo crear el archivo de log: " + e.getMessage());
        }
    }

    public static void init() {
        init(logFile);
    }

    public static void info(String mensaje) {
        if (writer != null) {
            String linea = "[" + LocalDateTime.now().format(FORMATTER) + "] " + mensaje;
            writer.println(linea);
            writer.flush();
        }
    }

    public static void separador() {
        info("=".repeat(80));
    }

    public static void close() {
        if (writer != null) {
            info("=== LOGGER CERRADO ===");
            writer.close();
            writer = null;
        }
    }
}
