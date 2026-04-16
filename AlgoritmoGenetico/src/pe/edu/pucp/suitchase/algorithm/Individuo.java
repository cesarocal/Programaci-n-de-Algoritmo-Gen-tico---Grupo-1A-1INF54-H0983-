import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Individuo {
    // El cromosoma es el orden en que se procesarán los envíos del bloque actual
    private List<EnvioAlgoritmo> cromosoma;
    private double fitness;

    public Individuo(List<EnvioAlgoritmo> envios) {
        this.cromosoma = new ArrayList<>(envios);
        // Mezclamos para crear un individuo inicial aleatorio
        Collections.shuffle(this.cromosoma);
        this.fitness = 0.0;
    }

    // Getters y Setters
    public List<EnvioAlgoritmo> getCromosoma() { return cromosoma; }
    public double getFitness() { return fitness; }
    public void setFitness(double fitness) { this.fitness = fitness; }
}
