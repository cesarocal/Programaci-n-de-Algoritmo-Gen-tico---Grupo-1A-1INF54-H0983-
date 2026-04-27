import java.util.*;

/**
 * Encapsula la solución de planificación producida por cualquiera de los
 * dos algoritmos (AG o ACS).
 *
 * Mapea cada EnvioAlgoritmo a su ResultadoRuta calculado, junto con
 * métricas de calidad de la solución.
 */
public class PlanificationSolutionOutput {

    /** Mapa de (origenOaci + "-" + idEnvio) -> ResultadoRuta */
    private final Map<String, ResultadoRuta> mapaRutas;

    /** Lista de envíos en el orden en que fueron planificados */
    private final List<EnvioAlgoritmo> enviosPlanificados;

    /** Estado de capacidades de vuelos al final de este bloque */
    private final Map<String, Integer> estadoCapacidadesVuelos;

    /** Estado de ocupación de almacenes al final de este bloque */
    private final Map<String, Integer> estadoOcupacionAlmacenes;

    /** Fitness / responsiveness del mejor individuo o colonia */
    private double metricaCalidad;

    /** Nombre del algoritmo que generó esta solución */
    private String algoritmo;

    public PlanificationSolutionOutput(String algoritmo) {
        this.algoritmo               = algoritmo;
        this.mapaRutas               = new LinkedHashMap<>();
        this.enviosPlanificados      = new ArrayList<>();
        this.estadoCapacidadesVuelos = new HashMap<>();
        this.estadoOcupacionAlmacenes = new HashMap<>();
        this.metricaCalidad          = Double.MAX_VALUE;
    }

    // ---- Rutas ----

    public void agregarRuta(EnvioAlgoritmo envio, ResultadoRuta ruta) {
        String clave = envio.getOrigenOaci() + "-" + envio.getId();
        mapaRutas.put(clave, ruta);
        if (!enviosPlanificados.contains(envio)) {
            enviosPlanificados.add(envio);
        }
    }

    public ResultadoRuta getRuta(EnvioAlgoritmo envio) {
        return mapaRutas.get(envio.getOrigenOaci() + "-" + envio.getId());
    }

    public Map<String, ResultadoRuta> getMapaRutas() {
        return mapaRutas;
    }

    public List<EnvioAlgoritmo> getEnviosPlanificados() {
        return enviosPlanificados;
    }

    // ---- Capacidades y almacenes ----

    public void setEstadoCapacidadesVuelos(Map<String, Integer> estado) {
        this.estadoCapacidadesVuelos.putAll(estado);
    }

    public void setEstadoOcupacionAlmacenes(Map<String, Integer> estado) {
        this.estadoOcupacionAlmacenes.putAll(estado);
    }

    public Map<String, Integer> getEstadoCapacidadesVuelos() {
        return estadoCapacidadesVuelos;
    }

    public Map<String, Integer> getEstadoOcupacionAlmacenes() {
        return estadoOcupacionAlmacenes;
    }

    // ---- Métricas ----

    public double getMetricaCalidad() { return metricaCalidad; }
    public void setMetricaCalidad(double m) { this.metricaCalidad = m; }

    public String getAlgoritmo() { return algoritmo; }

    // ---- Estadísticas rápidas ----

    public int totalEnvios() {
        return enviosPlanificados.size();
    }

    public long totalMaletas() {
        return enviosPlanificados.stream()
                .mapToLong(EnvioAlgoritmo::getCantidadMaletas).sum();
    }

    public int enviosConRuta() {
        return (int) enviosPlanificados.stream()
                .filter(e -> getRuta(e) != null).count();
    }
}
