import java.util.List;
import java.util.ArrayList;

public class PlanificationSolutionOutput {
    private List<Asignacion> asignaciones;

    public PlanificationSolutionOutput() {
        this.asignaciones = new ArrayList<>();
    }

    public PlanificationSolutionOutput(List<Asignacion> asignaciones) {
        this.asignaciones = new ArrayList<>(asignaciones);
    }

    public void agregarAsignacion(Asignacion a) {
        this.asignaciones.add(a);
    }

    public List<Asignacion> getAsignaciones() {
        return asignaciones;
    }
}
