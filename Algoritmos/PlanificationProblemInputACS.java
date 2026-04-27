import java.util.*;

/**
 * Input interno del ACS que trabaja con sus propias clases (Aeropuerto, Vuelo, Pedido).
 * Usado exclusivamente por AntColonySystem y VueloSelector.
 */
public class PlanificationProblemInputACS {

    private final Map<String, Aeropuerto> aeropuertos;
    private final Map<String, List<Vuelo>> vuelosPorOrigen;
    private final List<Pedido> pedidos;

    /** Ocupación global de vuelos compartida con el input canónico */
    private final Map<String, Integer> ocupacionGlobalVuelos;

    public PlanificationProblemInputACS(
            Map<String, Aeropuerto> aeropuertos,
            List<Vuelo> vuelos,
            List<Pedido> pedidos,
            Map<String, Integer> ocupacionGlobalVuelos) {

        this.aeropuertos           = aeropuertos;
        this.pedidos               = pedidos;
        this.ocupacionGlobalVuelos = ocupacionGlobalVuelos;

        this.vuelosPorOrigen = new HashMap<>();
        for (Vuelo v : vuelos) {
            this.vuelosPorOrigen
                    .computeIfAbsent(v.getOrigen(), k -> new ArrayList<>())
                    .add(v);
        }
    }

    public Aeropuerto getAeropuerto(String id) { return aeropuertos.get(id); }
    public Map<String, Aeropuerto> getAeropuertos() { return aeropuertos; }

    public List<Vuelo> getVuelosDesdeLinea(String origen) {
        return vuelosPorOrigen.getOrDefault(origen, new ArrayList<>());
    }

    public List<Pedido> getPedidos() { return pedidos; }

    public int getOcupacionGlobal(String flightKey) {
        return ocupacionGlobalVuelos.getOrDefault(flightKey, 0);
    }

    public void incrementarOcupacionGlobal(String flightKey, int cantidad) {
        ocupacionGlobalVuelos.merge(flightKey, cantidad, Integer::sum);
    }
}
