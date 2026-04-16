import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NodoRuta implements Comparable<NodoRuta> {
    public String aeropuertoActual;
    public LocalDateTime tiempoActual;
    public List<VueloAlgoritmo> rutaUsada;
    public List<LocalDateTime> fechasVuelosUsados;
    
    // Para A*
    public long minutosTranscurridos; // g(n)
    public double costoEstimadoTotal; // f(n) = g(n) + h(n)

    public NodoRuta(String aeropuertoActual, LocalDateTime tiempoActual, long minutosTranscurridos, double costoEstimadoTotal) {
        this.aeropuertoActual = aeropuertoActual;
        this.tiempoActual = tiempoActual;
        this.rutaUsada = new ArrayList<>();
        this.fechasVuelosUsados = new ArrayList<>();
        this.minutosTranscurridos = minutosTranscurridos;
        this.costoEstimadoTotal = costoEstimadoTotal;
    }

    // A* ordena la cola de prioridad basado en la mejor estimación total f(n)
    @Override
    public int compareTo(NodoRuta otro) {
        return Double.compare(this.costoEstimadoTotal, otro.costoEstimadoTotal);
    }
}
