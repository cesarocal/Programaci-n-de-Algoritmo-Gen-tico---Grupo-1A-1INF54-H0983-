import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACS adaptado según el paper "An Ant Colony System for Responsive Dynamic Vehicle Routing"
 * de M. Schyns, que a su vez sigue a Gambardella et al. (1999).
 *
 * Usa PlanificationProblemInputACS y PlanificationSolutionOutputACS internamente.
 * El adaptador ACSAdapter se encarga de la traducción hacia/desde las estructuras del AG.
 */
public class AntColonySystem {

    public static PlanificationSolutionOutputACS ACS_TASF(
            PlanificationProblemInputACS input, Instant reloj, long maxTiempoMs) {

        int n = input.getPedidos().size();
        if (n == 0) {
            return new PlanificationSolutionOutputACS(new ArrayList<>());
        }

        // --- 1. Inicialización con solución greedy ---
        PlanificationSolutionOutputACS psiStar = construirSolucionInicial_FIFO(input, reloj);
        double Rstar = calcularResponsiveness(psiStar, input);
        double Tstar = calcularDistanciaTotal(psiStar);

        double tau0 = Rstar > 0
                ? 1.0 / (n * Math.max(Rstar / n, 0.01))
                : 1.0 / Math.max(n, 1);

        Map<String, Double> feromonas = new ConcurrentHashMap<>();

        // --- 2. Parámetros del ACS ---
        final double rho       = 0.1;
        final int    mHormigas = 10;

        long tiempoInicio = System.currentTimeMillis();

        // --- 3. Bucle principal ---
        while (System.currentTimeMillis() - tiempoInicio < maxTiempoMs) {

            for (int h = 0; h < mHormigas; h++) {
                Ruta ruta = new Ruta();

                while (existeClienteAlcanzable(ruta, input)) {
                    Pedido pedido = seleccionarPedidoActivo(ruta, input);
                    if (pedido == null) break;

                    Vuelo vuelo = VueloSelector.seleccionarSiguienteVuelo(
                            pedido, ruta, feromonas, tau0, input, reloj);
                    if (vuelo == null) break;

                    ruta.agregarAsignacion(pedido, vuelo);

                    // Actualización LOCAL
                    String key = pedido.getId() + "-" + vuelo.getId();
                    double tauActual = feromonas.getOrDefault(key, tau0);
                    feromonas.put(key, (1 - rho) * tauActual + rho * tau0);
                }

                if (hayPedidosIncompletos(ruta, input)) {
                    ruta = intentarInsercionMultiHop(ruta, input);
                }

                ruta = busquedaLocalCROSS(ruta, input);

                double Rpsi = calcularResponsiveness(ruta.aPlanificationSolution(), input);
                double Tpsi = calcularDistanciaTotal(ruta.aPlanificationSolution());

                final double EPS = 1e-6;
                if (Rpsi < Rstar - EPS || (Math.abs(Rpsi - Rstar) < EPS && Tpsi < Tstar)) {
                    psiStar = ruta.aPlanificationSolution();
                    Rstar   = Rpsi;
                    Tstar   = Tpsi;
                }
            }

            // Actualización GLOBAL
            if (Rstar > 0) {
                for (Asignacion a : psiStar.getAsignaciones()) {
                    String key = a.getPedido().getId() + "-" + a.getVuelo().getId();
                    double tauActual = feromonas.getOrDefault(key, tau0);
                    feromonas.put(key, (1 - rho) * tauActual + rho / Rstar);
                }
            }
        }

        return psiStar;
    }

    // =======================================================================
    //  Métricas
    // =======================================================================

    public static double calcularResponsiveness(
            PlanificationSolutionOutputACS sol, PlanificationProblemInputACS input) {

        if (sol == null || sol.getAsignaciones().isEmpty()) return Double.MAX_VALUE / 2;

        Map<String, Vuelo>  ultimoVuelo = new HashMap<>();
        Map<String, String> ubicFinal  = new HashMap<>();

        for (Asignacion a : sol.getAsignaciones()) {
            String pid = a.getPedido().getId();
            ultimoVuelo.put(pid, a.getVuelo());
            ubicFinal.put(pid, a.getVuelo().getDestino());
        }

        double R = 0.0;
        for (Pedido p : input.getPedidos()) {
            Vuelo  vf        = ultimoVuelo.get(p.getId());
            String ubicacion = ubicFinal.getOrDefault(p.getId(), p.getOrigen());

            if (vf == null) {
                R += 48.0;
                continue;
            }
            if (!ubicacion.equals(p.getDestino())) {
                R += 72.0;
                continue;
            }

            double sj  = vf.getHoraLlegada().getHour() + vf.getHoraLlegada().getMinute() / 60.0;
            double oj  = duracionHorasUTC(vf, input);
            double aj  = p.getTiempoCreacion().getHour() + p.getTiempoCreacion().getMinute() / 60.0;
            double Rij = sj + oj - aj;
            if (Rij < 0) Rij += 24.0;
            R += Math.max(0, Rij);
        }
        return R;
    }

    public static double calcularDistanciaTotal(PlanificationSolutionOutputACS sol) {
        if (sol == null) return Double.MAX_VALUE;
        double t = 0.0;
        for (Asignacion a : sol.getAsignaciones()) {
            Vuelo v = a.getVuelo();
            double s = v.getHoraSalida().toSecondOfDay() / 3600.0;
            double l = v.getHoraLlegada().toSecondOfDay() / 3600.0;
            if (l < s) l += 24.0;
            t += (l - s);
        }
        return t;
    }

    // =======================================================================
    //  Solución inicial greedy (FIFO)
    // =======================================================================

    private static PlanificationSolutionOutputACS construirSolucionInicial_FIFO(
            PlanificationProblemInputACS input, Instant reloj) {

        Ruta rutaTemp = new Ruta();
        for (Pedido p : input.getPedidos()) {
            int saltos = 0;
            while (!rutaTemp.getUbicacionActual(p).equals(p.getDestino()) && saltos < 20) {
                List<Vuelo> vuelos = VueloSelector.candidatosViables(p, rutaTemp, input);
                if (vuelos == null || vuelos.isEmpty()) break;
                Vuelo elegido = null;
                for (Vuelo v : vuelos) {
                    elegido = v;
                    if (v.getDestino().equals(p.getDestino())) break;
                }
                if (elegido == null) break;
                rutaTemp.agregarAsignacion(p, elegido);
                saltos++;
            }
        }
        Logger.info("ACS - Solución inicial FIFO construida.");
        return rutaTemp.aPlanificationSolution();
    }

    // =======================================================================
    //  Auxiliares
    // =======================================================================

    private static boolean existeClienteAlcanzable(Ruta ruta, PlanificationProblemInputACS input) {
        for (Pedido p : input.getPedidos()) {
            if (!ruta.getUbicacionActual(p).equals(p.getDestino())) {
                for (Vuelo v : input.getVuelosDesdeLinea(ruta.getUbicacionActual(p))) {
                    if (!ruta.haVisitadoAeropuerto(p, v.getDestino())
                            && v.getCapacidad() - ruta.getOcupacionVuelo(v.getId()) >= p.getCantidadMaletas()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Pedido seleccionarPedidoActivo(Ruta ruta, PlanificationProblemInputACS input) {
        List<Pedido> candidatos = new ArrayList<>();
        for (Pedido p : input.getPedidos()) {
            if (ruta.getUbicacionActual(p).equals(p.getDestino())) continue;
            for (Vuelo v : input.getVuelosDesdeLinea(ruta.getUbicacionActual(p))) {
                if (!ruta.haVisitadoAeropuerto(p, v.getDestino())
                        && v.getCapacidad() - ruta.getOcupacionVuelo(v.getId()) >= p.getCantidadMaletas()) {
                    candidatos.add(p);
                    break;
                }
            }
        }
        if (candidatos.isEmpty()) return null;
        return candidatos.get(new Random().nextInt(candidatos.size()));
    }

    private static boolean hayPedidosIncompletos(Ruta ruta, PlanificationProblemInputACS input) {
        for (Pedido p : input.getPedidos()) {
            if (!ruta.getUbicacionActual(p).equals(p.getDestino())) return true;
        }
        return false;
    }

    private static Ruta busquedaLocalCROSS(Ruta ruta, PlanificationProblemInputACS input) {
        List<Asignacion> limpias = new ArrayList<>();
        for (Pedido p : input.getPedidos()) {
            List<Asignacion> rp = new ArrayList<>();
            for (Asignacion a : ruta.getAsignaciones()) {
                if (a.getPedido().getId().equals(p.getId())) rp.add(a);
            }
            for (int i = 0; i < rp.size(); i++) {
                for (int j = rp.size() - 1; j > i; j--) {
                    if (rp.get(i).getVuelo().getOrigen().equals(rp.get(j).getVuelo().getDestino())) {
                        rp.subList(i + 1, j + 1).clear();
                        break;
                    }
                }
            }
            limpias.addAll(rp);
        }
        return new Ruta(limpias);
    }

    private static Ruta intentarInsercionMultiHop(Ruta ruta, PlanificationProblemInputACS input) {
        final int MAX_SALTOS = 5;
        for (Pedido p : input.getPedidos()) {
            if (ruta.getUbicacionActual(p).equals(p.getDestino())) continue;

            String origen  = ruta.getUbicacionActual(p);
            String destino = p.getDestino();

            Queue<List<Vuelo>> cola      = new LinkedList<>();
            Set<String>        visitados = new HashSet<>();
            cola.add(new ArrayList<>());
            visitados.add(origen);

            bfsLoop:
            while (!cola.isEmpty()) {
                List<Vuelo> camino = cola.poll();
                if (camino.size() >= MAX_SALTOS) continue;
                String desde = camino.isEmpty() ? origen : camino.get(camino.size() - 1).getDestino();

                for (Vuelo v : input.getVuelosDesdeLinea(desde)) {
                    if (visitados.contains(v.getDestino())) continue;
                    if (v.getCapacidad() - ruta.getOcupacionVuelo(v.getId()) < p.getCantidadMaletas()) continue;

                    List<Vuelo> nuevoCamino = new ArrayList<>(camino);
                    nuevoCamino.add(v);

                    if (v.getDestino().equals(destino)) {
                        for (Vuelo vi : nuevoCamino) ruta.agregarAsignacion(p, vi);
                        break bfsLoop;
                    }
                    visitados.add(v.getDestino());
                    cola.add(nuevoCamino);
                }
            }
        }
        return ruta;
    }

    private static double duracionHorasUTC(Vuelo v, PlanificationProblemInputACS input) {
        double s = v.getHoraSalida().toSecondOfDay()  / 3600.0;
        double l = v.getHoraLlegada().toSecondOfDay() / 3600.0;
        Aeropuerto src = input.getAeropuerto(v.getOrigen());
        Aeropuerto dst = input.getAeropuerto(v.getDestino());
        if (src != null && dst != null) {
            s -= src.getHusoHorario();
            l -= dst.getHusoHorario();
        }
        double dur = l - s;
        if (dur <= 0) dur += 24.0;
        return dur;
    }
}
