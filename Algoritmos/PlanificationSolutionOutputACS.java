import java.util.*;

/**
 * Output interno del ACS. Contiene las asignaciones (Pedido, Vuelo)
 * producidas por AntColonySystem.
 */
public class PlanificationSolutionOutputACS {
    private final List<Asignacion> asignaciones;

    public PlanificationSolutionOutputACS() {
        this.asignaciones = new ArrayList<>();
    }

    public PlanificationSolutionOutputACS(List<Asignacion> asignaciones) {
        this.asignaciones = new ArrayList<>(asignaciones);
    }

    public void agregarAsignacion(Asignacion a) { asignaciones.add(a); }
    public List<Asignacion> getAsignaciones() { return asignaciones; }
}
