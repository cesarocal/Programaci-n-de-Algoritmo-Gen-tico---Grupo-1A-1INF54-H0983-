import java.time.LocalTime;

public class Vuelo {
    private String id; // Origen-Destino-Ho-Hd
    private String origen;
    private String destino;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private int capacidad;

    public Vuelo(String origen, String destino, LocalTime horaSalida, LocalTime horaLlegada, int capacidad) {
        this.origen = origen;
        this.destino = destino;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.capacidad = capacidad;
        this.id = origen + "-" + destino + "-" + horaSalida.toString() + "-" + horaLlegada.toString();
    }

    public String getId() { return id; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public LocalTime getHoraSalida() { return horaSalida; }
    public LocalTime getHoraLlegada() { return horaLlegada; }
    public int getCapacidad() { return capacidad; }
}
