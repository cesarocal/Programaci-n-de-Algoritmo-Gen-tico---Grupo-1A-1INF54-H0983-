import java.time.LocalTime;

public class VueloAlgoritmo {


    private String origenOaci;
    private String destinoOaci;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private int capacidad;

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

    public LocalTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(LocalTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public LocalTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(LocalTime horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    @Override
    public String toString() {
        return "Vuelo: " + origenOaci + " -> " + destinoOaci + " | Salida: " + horaSalida + " Llegada: " + horaLlegada + " | Cap: " + capacidad;
    }
}
