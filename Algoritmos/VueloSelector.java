import java.time.Instant;
import java.util.*;

/**
 * Selector de vuelo para el ACS (Ant Colony System).
 * Implementa la heurística y regla de transición del paper de Schyns.
 * Usa PlanificationProblemInputACS internamente.
 */
public class VueloSelector {

    private static final Random random = new Random();

    public static Vuelo seleccionarSiguienteVuelo(
            Pedido p, Ruta ruta, Map<String, Double> feromonas,
            double tau0, PlanificationProblemInputACS input, Instant reloj) {

        List<Vuelo> Ni = candidatosViables(p, ruta, input);
        if (Ni == null || Ni.isEmpty()) return null;

        final double q0   = 0.9;
        final double beta = 2.0;

        double wi = tiempoDisponibleActual(p, ruta);
        double aj = p.getTiempoCreacion().getHour() + p.getTiempoCreacion().getMinute() / 60.0;

        double[] valor     = new double[Ni.size()];
        double   sumaValor = 0.0;
        int      mejorIdx  = 0;
        double   mejorVal  = -1.0;

        for (int idx = 0; idx < Ni.size(); idx++) {
            Vuelo j   = Ni.get(idx);
            double tij = duracionHorasUTC(j, input);
            double sj  = Math.max(wi + tij, aj);
            double oj  = tij;
            double Rij = sj + oj - aj;
            if (Rij <= 0) Rij = 0.001;
            double eta_ij = 1.0 / Rij;

            String key    = p.getId() + "-" + j.getId();
            double tau_ij = feromonas.getOrDefault(key, tau0);
            double v      = tau_ij * Math.pow(eta_ij, beta);
            valor[idx]    = v;
            sumaValor    += v;
            if (v > mejorVal) { mejorVal = v; mejorIdx = idx; }
        }

        if (random.nextDouble() <= q0) return Ni.get(mejorIdx);

        if (sumaValor <= 0) return Ni.get(random.nextInt(Ni.size()));
        double tirada = random.nextDouble() * sumaValor;
        double acum   = 0.0;
        for (int idx = 0; idx < Ni.size(); idx++) {
            acum += valor[idx];
            if (acum >= tirada) return Ni.get(idx);
        }
        return Ni.get(Ni.size() - 1);
    }

    // ---- Disponibilidad absoluta del pedido en su nodo actual ----

    /**
     * Tiempo mínimo de manipulación de maleta en cualquier aeropuerto:
     *   - En escala : la maleta debe estar al menos HANDLING_MINUTES en el almacén
     *                 antes de poder embarcar en el siguiente vuelo.
     *   - En destino: tiempo entre llegada y recogida por el cliente.
     * Se suma después de cada aterrizaje, por lo que candidatosViables y el BFS
     * multi-hop lo heredan automáticamente al llamar a este método.
     */
    public static final int HANDLING_MINUTES = 10;

    public static java.time.LocalDateTime getDisponibilidadAbsoluta(Pedido p, Ruta ruta) {
        java.time.LocalDateTime actual = p.getTiempoCreacion();
        for (Asignacion a : ruta.getAsignaciones()) {
            if (!a.getPedido().getId().equals(p.getId())) continue;
            Vuelo v = a.getVuelo();
            java.time.LocalDateTime salida = actual.with(v.getHoraSalida());
            if (salida.isBefore(actual)) salida = salida.plusDays(1);
            java.time.LocalDateTime llegada = salida.with(v.getHoraLlegada());
            if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);
            // Tiempo de manipulación: mínimo 10 min en almacén tras cada aterrizaje
            actual = llegada.plusMinutes(HANDLING_MINUTES);
        }
        return actual;
    }

    // ---- Candidatos viables ----

    public static List<Vuelo> candidatosViables(
            Pedido p, Ruta ruta, PlanificationProblemInputACS input) {

        String desde = ruta.getUbicacionActual(p);
        List<Vuelo> viables = new ArrayList<>();
        java.time.LocalDateTime disp = getDisponibilidadAbsoluta(p, ruta);

        for (Vuelo v : input.getVuelosDesdeLinea(desde)) {
            if (ruta.haVisitadoAeropuerto(p, v.getDestino())) continue;

            java.time.LocalDateTime salida = disp.with(v.getHoraSalida());
            if (salida.isBefore(disp)) salida = salida.plusDays(1);
            java.time.LocalDateTime llegada = salida.with(v.getHoraLlegada());
            if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

            if (llegada.isAfter(p.getTiempoLimite())) continue;

            String flightKey = v.getId() + "-" + salida.toLocalDate().toString();
            int usoGlobal = input.getOcupacionGlobal(flightKey);
            int usoLocal  = ruta.getOcupacionVuelo(flightKey);
            if (v.getCapacidad() - (usoGlobal + usoLocal) < p.getCantidadMaletas()) continue;

            viables.add(v);
        }
        return viables;
    }

    // ---- Helpers privados ----

    private static double tiempoDisponibleActual(Pedido p, Ruta ruta) {
        java.time.LocalDateTime disp = getDisponibilidadAbsoluta(p, ruta);
        return java.time.Duration.between(p.getTiempoCreacion(), disp).toMinutes() / 60.0;
    }

    private static double duracionHorasUTC(Vuelo j, PlanificationProblemInputACS input) {
        double s = j.getHoraSalida().toSecondOfDay()  / 3600.0;
        double l = j.getHoraLlegada().toSecondOfDay() / 3600.0;
        Aeropuerto src = input.getAeropuerto(j.getOrigen());
        Aeropuerto dst = input.getAeropuerto(j.getDestino());
        if (src != null && dst != null) {
            s -= src.getHusoHorario();
            l -= dst.getHusoHorario();
        }
        double dur = l - s;
        if (dur <= 0) dur += 24.0;
        return dur;
    }
}
