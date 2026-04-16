import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class PlanificationProblemInput {
    private Map<String, Aeropuerto> aeropuertos;
    private Map<String, List<Vuelo>> vuelosDesdeAeropuerto;
    private List<Pedido> pedidos;
    private Map<String, Integer> ocupacionGlobalVuelos;

    public PlanificationProblemInput() {
        this.aeropuertos = new HashMap<>();
        this.vuelosDesdeAeropuerto = new HashMap<>();
        this.pedidos = new ArrayList<>();
        this.ocupacionGlobalVuelos = new HashMap<>();
    }

    public void agregarAeropuerto(Aeropuerto a) {
        aeropuertos.put(a.getId(), a);
    }

    public void agregarVuelo(Vuelo v) {
        vuelosDesdeAeropuerto.computeIfAbsent(v.getOrigen(), k -> new ArrayList<>()).add(v);
    }

    public void agregarPedido(Pedido p) {
        pedidos.add(p);
    }

    public Map<String, Aeropuerto> getAeropuertos() { return aeropuertos; }
    public Aeropuerto getAeropuerto(String id) { return aeropuertos.get(id); }
    public List<Vuelo> getVuelosDesdeLinea(String origen) {
        return vuelosDesdeAeropuerto.getOrDefault(origen, new ArrayList<>());
    }
    public List<Pedido> getPedidos() { return pedidos; }

    public int getOcupacionGlobal(String flightKey) {
        return ocupacionGlobalVuelos.getOrDefault(flightKey, 0);
    }
    public void incrementarOcupacionGlobal(String flightKey, int cantidad) {
        ocupacionGlobalVuelos.put(flightKey, getOcupacionGlobal(flightKey) + cantidad);
    }

    /**
     * Crea un sub-input que COMPARTE aeropuertos, vuelos y capacidad (por referencia)
     * pero tiene su propia lista de pedidos.
     */
    public PlanificationProblemInput crearSubInput(List<Pedido> subPedidos) {
        PlanificationProblemInput sub = new PlanificationProblemInput();
        sub.aeropuertos = this.aeropuertos;
        sub.vuelosDesdeAeropuerto = this.vuelosDesdeAeropuerto;
        sub.pedidos = new ArrayList<>(subPedidos);
        sub.ocupacionGlobalVuelos = this.ocupacionGlobalVuelos;
        return sub;
    }
}
