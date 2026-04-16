import java.time.LocalDateTime;

public class Pedido {
    private String id;
    private String origen;
    private String destino;
    private LocalDateTime tiempoCreacion;
    private int cantidadMaletas;
    private String cliente;
    private boolean completado;

    // Plazo
    private LocalDateTime tiempoLimite;

    public Pedido(String id, String origen, String destino, LocalDateTime tiempoCreacion, int cantidadMaletas, String cliente) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.tiempoCreacion = tiempoCreacion;
        this.cantidadMaletas = cantidadMaletas;
        this.cliente = cliente;
        this.completado = false;
    }

    public String getId() { return id; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public LocalDateTime getTiempoCreacion() { return tiempoCreacion; }
    public int getCantidadMaletas() { return cantidadMaletas; }
    public String getCliente() { return cliente; }
    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }
    public LocalDateTime getTiempoLimite() { return tiempoLimite; }
    public void setTiempoLimite(LocalDateTime tiempoLimite) { this.tiempoLimite = tiempoLimite; }
}
