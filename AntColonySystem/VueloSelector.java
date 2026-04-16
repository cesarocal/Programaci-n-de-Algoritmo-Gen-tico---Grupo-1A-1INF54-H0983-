import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Random;

/**
 * Seleccion del siguiente vuelo siguiendo el paper de Schyns (adaptado de Gambardella 1999).
 *
 * HEURISTICA (formula del paper, pag 9-10):
 *   eta_ij = 1 / R_ij
 *   R_ij = sj + oj - aj
 *   donde:
 *     sj  = max(wi + t_ij, aj)    -> hora de inicio de servicio en j
 *     oj  = duracion del servicio (duracion del vuelo en este problema)
 *     aj  = apertura de la ventana de tiempo de j (hora de creacion del pedido)
 *     wi  = hora en que la hormiga queda disponible despues de servir i
 *
 * REGLA DE TRANSICION (Algorithm 2 del paper):
 *   - Con probabilidad q0: EXPLOTACION -> elegir argmax[tau * eta^beta]
 *   - Con prob. (1-q0):   EXPLORACION -> ruleta proporcional a tau * eta^beta
 */
public class VueloSelector {

    private static final Random random = new Random();

    public static Vuelo SeleccionarSiguienteVuelo(
            Pedido p, Ruta ruta, Map<String, Double> feromonas,
            double tau0, PlanificationProblemInput input, Instant reloj) {

        List<Vuelo> Ni = candidatosViables(p, ruta, input);
        if (Ni == null || Ni.isEmpty()) return null;

        final double q0   = 0.9;   // factor de explotacion
        final double beta = 2.0;   // peso de la heuristica

        // wi = hora en que el pedido queda disponible en el nodo actual
        double wi = tiempoDisponibleActual(p, ruta);
        // aj = apertura de ventana = hora de creacion del pedido
        double aj = p.getTiempoCreacion().getHour() + p.getTiempoCreacion().getMinute() / 60.0;

        double[] valor    = new double[Ni.size()];
        double   sumaValor = 0.0;
        int      mejorIdx  = 0;
        double   mejorVal  = -1.0;

        for (int idx = 0; idx < Ni.size(); idx++) {
            Vuelo j = Ni.get(idx);

            // t_ij: tiempo de vuelo (duracion en horas UTC)
            double tij = duracionHorasUTC(j, input);

            // sj = max(wi + t_ij, aj)  -- hora de inicio del servicio en j
            double sj = Math.max(wi + tij, aj);

            // o_j: duracion del servicio en j (mismo vuelo en este problema)
            double oj = tij;

            // R_ij = sj + oj - aj  (responsiveness marginal del movimiento)
            double Rij = sj + oj - aj;
            if (Rij <= 0) Rij = 0.001; // proteccion
            double eta_ij = 1.0 / Rij;

            String key    = p.getId() + "-" + j.getId();
            double tau_ij = feromonas.getOrDefault(key, tau0);

            double v = tau_ij * Math.pow(eta_ij, beta);
            valor[idx]  = v;
            sumaValor  += v;

            if (v > mejorVal) { mejorVal = v; mejorIdx = idx; }
        }

        // Regla de transicion del paper (Algorithm 2)
        if (random.nextDouble() <= q0) {
            // EXPLOTACION: argmax
            return Ni.get(mejorIdx);
        }

        // EXPLORACION: ruleta proporcional
        if (sumaValor <= 0) return Ni.get(random.nextInt(Ni.size()));
        double tirada = random.nextDouble() * sumaValor;
        double acum   = 0.0;
        for (int idx = 0; idx < Ni.size(); idx++) {
            acum += valor[idx];
            if (acum >= tirada) return Ni.get(idx);
        }
        return Ni.get(Ni.size() - 1);
    }

    // -----------------------------------------------------------------------

    /** Calcula la disponibilidad ABSOLUTA (fecha y hora) del pedido en su nodo actual */
    public static java.time.LocalDateTime getDisponibilidadAbsoluta(Pedido p, Ruta ruta) {
        java.time.LocalDateTime actual = p.getTiempoCreacion();
        for (Asignacion a : ruta.getAsignaciones()) {
            if (!a.getPedido().getId().equals(p.getId())) continue;
            Vuelo v = a.getVuelo();
            java.time.LocalDateTime salida = actual.with(v.getHoraSalida());
            if (salida.isBefore(actual)) salida = salida.plusDays(1);
            
            java.time.LocalDateTime llegada = salida.with(v.getHoraLlegada());
            if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);
            
            actual = llegada;
        }
        return actual;
    }

    /** Vuelos candidatos: desde ubicacion actual, sin ciclos, con capacidad, y DENTRO DEL TIEMPO LIMITE. */
    public static List<Vuelo> candidatosViables(
            Pedido p, Ruta ruta, PlanificationProblemInput input) {
        String desde = ruta.getUbicacionActual(p);
        List<Vuelo> viables = new ArrayList<>();
        java.time.LocalDateTime disp = getDisponibilidadAbsoluta(p, ruta);

        for (Vuelo v : input.getVuelosDesdeLinea(desde)) {
            if (ruta.haVisitadoAeropuerto(p, v.getDestino())) continue;

            // Simular hora de llegada y salida absolutas
            java.time.LocalDateTime salida = disp.with(v.getHoraSalida());
            if (salida.isBefore(disp)) salida = salida.plusDays(1);
            java.time.LocalDateTime llegada = salida.with(v.getHoraLlegada());
            if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

            // Filtro ultra importante: Limite de horas (24h/48h)
            if (llegada.isAfter(p.getTiempoLimite())) continue;

            // Verificar Capacidad combinada (Acumulado global de bloques anteriores + enrutamiento local)
            String flightKey = v.getId() + "-" + salida.toLocalDate().toString();
            int usoGlobal = input.getOcupacionGlobal(flightKey);
            int usoLocal = ruta.getOcupacionVuelo(flightKey);
            if (v.getCapacidad() - (usoGlobal + usoLocal) < p.getCantidadMaletas()) continue;

            viables.add(v);
        }
        return viables;
    }

    /**
     * wi = horas absolutas transcurridas desde la creacion del pedido hasta la disponibilidad actual.
     */
    private static double tiempoDisponibleActual(Pedido p, Ruta ruta) {
        java.time.LocalDateTime disp = getDisponibilidadAbsoluta(p, ruta);
        return java.time.Duration.between(p.getTiempoCreacion(), disp).toMinutes() / 60.0;
    }

    /** Duracion del vuelo normalizada a UTC para evitar distorsion por huso horario. */
    private static double duracionHorasUTC(Vuelo j, PlanificationProblemInput input) {
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
