import java.time.LocalDateTime;
import java.util.List;

public class ResultadoRuta {
    LocalDateTime tiempoLlegadaFinal;
    List<VueloAlgoritmo> vuelosUsados;
    List<LocalDateTime> fechasVuelo;

    public ResultadoRuta(LocalDateTime tiempo, List<VueloAlgoritmo> vuelos, List<LocalDateTime> fechas) {
        this.tiempoLlegadaFinal = tiempo;
        this.vuelosUsados = vuelos;
        this.fechasVuelo = fechas;
    }
}