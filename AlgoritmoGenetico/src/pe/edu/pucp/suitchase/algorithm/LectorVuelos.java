import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LectorVuelos {
    public List<VueloAlgoritmo> leerVuelos(String rutaArchivo, List<AeropuertoAlgoritmo> aeropuertos) {
        List<VueloAlgoritmo> vuelos = new ArrayList<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // Diccionario rápido de GMTs
        Map<String, Integer> mapaGmt = new HashMap<>();
        for (AeropuertoAlgoritmo aero : aeropuertos) {
            mapaGmt.put(aero.getOaci(), aero.getGmt());
        }

        try {
            List<String> lineas = Files.readAllLines(Paths.get(rutaArchivo), StandardCharsets.ISO_8859_1);
            for (String linea : lineas) {
                if (linea.trim().isEmpty()) continue;
                String[] datos = linea.split("-");
                if (datos.length >= 5) {
                    VueloAlgoritmo vuelo = new VueloAlgoritmo();
                    String origen = datos[0].trim();
                    String destino = datos[1].trim();
                    vuelo.setOrigenOaci(origen);
                    vuelo.setDestinoOaci(destino);

                    // Obtener GMTs (por defecto 0 si no se encuentra)
                    int gmtOrigen = mapaGmt.getOrDefault(origen, 0);
                    int gmtDestino = mapaGmt.getOrDefault(destino, 0);

                    // Convertir horas locales a GMT-0
                    LocalTime salidaLocal = LocalTime.parse(datos[2].trim(), timeFormatter);
                    LocalTime llegadaLocal = LocalTime.parse(datos[3].trim(), timeFormatter);

                    LocalTime salidaGmt = salidaLocal.minusHours(gmtOrigen);
                    LocalTime llegadaGmt = llegadaLocal.minusHours(gmtDestino);

                    vuelo.setHoraSalida(salidaGmt);
                    vuelo.setHoraLlegada(llegadaGmt);
                    vuelo.setCapacidad(Integer.parseInt(datos[4].trim()));

                    vuelos.add(vuelo);
                }
            }
        } catch (IOException e) {
            System.err.println("Error leyendo vuelos: " + e.getMessage());
        }
        return vuelos;
    }
}
