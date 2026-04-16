import java.time.LocalDateTime;

public class EnvioAlgoritmo {
    private String id;
    private LocalDateTime fechaHoraRegistro;
    private String origenOaci;
    private String destinoOaci;
    private int cantidadMaletas;
    private String clienteId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getFechaHoraRegistro() {
        return fechaHoraRegistro;
    }

    public void setFechaHoraRegistro(LocalDateTime fechaHoraRegistro) {
        this.fechaHoraRegistro = fechaHoraRegistro;
    }

    public String getOrigenOaci() {
        return origenOaci;
    }

    public void setOrigenOaci(String origenOaci) {
        this.origenOaci = origenOaci;
    }

    public String getDestinoOaci() {
        return destinoOaci;
    }

    public void setDestinoOaci(String destinoOaci) {
        this.destinoOaci = destinoOaci;
    }

    public int getCantidadMaletas() {
        return cantidadMaletas;
    }

    public void setCantidadMaletas(int cantidadMaletas) {
        this.cantidadMaletas = cantidadMaletas;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    @Override
    public String toString() {
        return "Envío [" + id + "]: " + origenOaci + " -> " + destinoOaci + " | " + cantidadMaletas + " maletas | Registro: " + fechaHoraRegistro;
    }
}
