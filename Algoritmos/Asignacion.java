public class Asignacion {
    private Pedido pedido;
    private Vuelo vuelo;
    private String flightKey;

    public Asignacion(Pedido pedido, Vuelo vuelo, String flightKey) {
        this.pedido = pedido;
        this.vuelo = vuelo;
        this.flightKey = flightKey;
    }

    public Pedido getPedido() { return pedido; }
    public Vuelo getVuelo() { return vuelo; }
    public String getFlightKey() { return flightKey; }
}
