import java.time.LocalDateTime;
import java.util.List;

public class ContadorMaletas {
    public static void main(String[] args) {
        LectorAeropuertos lectorAero = new LectorAeropuertos();
        List<AeropuertoAlgoritmo> aeropuertos = lectorAero.leerAeropuertos("c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt");

        LectorEnvios lectorEnvios = new LectorEnvios();
        LocalDateTime inicio = LocalDateTime.of(2028, 12, 31, 0, 0);
        LocalDateTime fin = LocalDateTime.of(2029, 1, 1, 0, 0);

        lectorEnvios.cargarTodosLosEnvios("_envios_preliminar_", aeropuertos, inicio, fin);

        int totalEnvios = 0;
        int totalMaletas = 0;
        
        lectorEnvios.configurarRangoTemporal(inicio);
        
        while (lectorEnvios.hayMasEnvios()) {
            List<EnvioAlgoritmo> bloque = lectorEnvios.obtenerEnviosPorVentanaDeTiempo(24 * 60);
            totalEnvios += bloque.size();
            for (EnvioAlgoritmo e : bloque) {
                totalMaletas += e.getCantidadMaletas();
            }
        }
        
        System.out.println("RESULTADO_TOTAL_ENVIOS: " + totalEnvios);
        System.out.println("RESULTADO_TOTAL_MALETAS: " + totalMaletas);
    }
}
