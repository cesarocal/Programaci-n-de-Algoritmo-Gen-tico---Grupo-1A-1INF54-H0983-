import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACS adaptado segun el paper "An Ant Colony System for Responsive Dynamic Vehicle Routing"
 * de M. Schyns, que a su vez sigue a Gambardella et al. (1999).
 *
 * Puntos clave del paper implementados aqui:
 *
 * 1. tau0 = 1 / (n * R*)  donde R* = responsiveness de la solucion greedy inicial.
 *
 * 2. Actualizacion LOCAL (cada movimiento de hormiga):
 *       tau(i,j) <- (1-rho)*tau(i,j) + rho*tau0
 *    Proposito: reducir atractivo del arco visitado, forzar diversificacion.
 *
 * 3. Actualizacion GLOBAL (al final del grupo de hormigas, solo sobre psi*):
 *       tau(i,j) <- (1-rho)*tau(i,j) + rho/R*
 *    Proposito: intensificar sobre la mejor solucion global.
 *
 * 4. La heuristica eta_ij = 1/R_ij donde R_ij = sj + oj - aj
 *    (responsiveness marginal del movimiento i -> j).
 *
 * 5. Criterio de mejora: (R_psi < R*) OR (R_psi == R* AND T_psi < T*)
 *    Es decir, se minimiza la responsiveness total (no se maximiza fitness).
 */
public class AntColonySystem {

    public static PlanificationSolutionOutput ACS_TASF(PlanificationProblemInput input, Instant reloj, long maxTiempoMs) {

        int n = input.getPedidos().size();
        if (n == 0) {
            return new PlanificationSolutionOutput(new ArrayList<>());
        }

        // --- 1. Inicializacion con solucion greedy ---
        PlanificationSolutionOutput psiStar = ConstruirSolucionInicial_FIFO(input, reloj);
        double Rstar = calcularResponsiveness(psiStar, input);
        double Tstar = calcularDistanciaTotal(psiStar);

        // tau0 = 1 / (n * R*)
        double tau0 = Rstar > 0 ? 1.0 / (n * Math.max(Rstar / n, 0.01)) : 1.0 / Math.max(n, 1);

        // Matriz esparsa de feromonas
        Map<String, Double> feromonas = new ConcurrentHashMap<>();

        // --- 2. Parametros del ACS ---
        final double rho       = 0.1;
        final int    mHormigas = 10;
        final long   MAX_TIEMPO = maxTiempoMs;

        long tiempoInicio = System.currentTimeMillis();
        long ultimoPrint  = tiempoInicio;
        long iteracion    = 0;

        // --- 3. Bucle principal (Algorithm 1 del paper) ---
        while (System.currentTimeMillis() - tiempoInicio < MAX_TIEMPO) {
            iteracion++;

            for (int h = 0; h < mHormigas; h++) {
                Ruta ruta = new Ruta();

                // Construccion del path por la hormiga (lineas 10-12 del algoritmo)
                while (existeClienteAlcanzable(ruta, input)) {
                    Pedido pedido = SeleccionarPedidoActivo(ruta, input);
                    if (pedido == null) break;

                    Vuelo vuelo = VueloSelector.SeleccionarSiguienteVuelo(
                            pedido, ruta, feromonas, tau0, input, reloj);
                    if (vuelo == null) break;

                    ruta.agregarAsignacion(pedido, vuelo);

                    // ACTUALIZACION LOCAL: tau(i,j) <- (1-rho)*tau(i,j) + rho*tau0
                    String key = pedido.getId() + "-" + vuelo.getId();
                    double tauActual = feromonas.getOrDefault(key, tau0);
                    feromonas.put(key, (1 - rho) * tauActual + rho * tau0);
                }

                // Linea 13: intentar insertar clientes no servidos (BFS multi-hop)
                if (hayPedidosIncompletos(ruta, input)) {
                    ruta = IntentarInsercionMultiHop(ruta, input);
                }

                // SIEMPRE evaluar la solucion (completa o parcial con penalizacion)
                // Esto permite que R* mejore gradualmente
                ruta = BusquedaLocalCROSS(ruta, input);

                double Rpsi = calcularResponsiveness(ruta.aPlanificationSolution(), input);
                double Tpsi = calcularDistanciaTotal(ruta.aPlanificationSolution());

                // Criterio de mejora del paper con epsilon para igualdad de doubles
                final double EPS = 1e-6;
                if (Rpsi < Rstar - EPS || (Math.abs(Rpsi - Rstar) < EPS && Tpsi < Tstar)) {
                    psiStar = ruta.aPlanificationSolution();
                    Rstar   = Rpsi;
                    Tstar   = Tpsi;
                }
            }

            // ACTUALIZACION GLOBAL
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
    //  Metricas segun el paper
    // =======================================================================

    /**
     * R = sum_j (s_j + o_j - a_j)   (responsiveness total a minimizar, segun el paper)
     *
     * Penalizaciones adicionales para el problema de rutas:
     *  - Pedido sin asignacion alguna:              +48h (no hay vuelo)
     *  - Pedido asignado pero NO en su destino:     +72h (entrega incompleta -- penalizacion fuerte)
     *  - Pedido en su destino:                      formula normal del paper
     */
    public static double calcularResponsiveness(PlanificationSolutionOutput sol, PlanificationProblemInput input) {
        if (sol == null || sol.getAsignaciones().isEmpty()) return Double.MAX_VALUE / 2;

        // Ultimo vuelo y ubicacion final de cada pedido
        Map<String, Vuelo>  ultimoVuelo   = new HashMap<>();
        Map<String, String> ubicFinal     = new HashMap<>();

        for (Asignacion a : sol.getAsignaciones()) {
            String pid = a.getPedido().getId();
            ultimoVuelo.put(pid, a.getVuelo());
            // La ubicacion final es el destino del ultimo vuelo asignado
            ubicFinal.put(pid, a.getVuelo().getDestino());
        }

        double R = 0.0;
        for (Pedido p : input.getPedidos()) {
            Vuelo vf         = ultimoVuelo.get(p.getId());
            String ubicacion = ubicFinal.getOrDefault(p.getId(), p.getOrigen());

            if (vf == null) {
                // Sin asignacion: penalizacion leve (quiza no hay vuelos desde ese origen)
                R += 48.0;
                continue;
            }

            if (!ubicacion.equals(p.getDestino())) {
                // Asignado pero NO llego al destino: penalizacion fuerte
                R += 72.0;
                continue;
            }

            // Entregado correctamente: formula del paper
            double sj = vf.getHoraLlegada().getHour() + vf.getHoraLlegada().getMinute() / 60.0;
            double oj = duracionHorasUTC(vf, input);
            double aj = p.getTiempoCreacion().getHour() + p.getTiempoCreacion().getMinute() / 60.0;
            double Rij = sj + oj - aj;
            if (Rij < 0) Rij += 24.0;
            R += Math.max(0, Rij);
        }
        return R;
    }

    /** Distancia total aproximada = suma de duraciones de vuelo (en horas). Usada como desempate. */
    public static double calcularDistanciaTotal(PlanificationSolutionOutput sol) {
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

    /** Compatibilidad: devuelve fitness [0,1] para el log de salida (mayor = mejor). */
    public static double CalcularFitness_ACS(
            PlanificationSolutionOutput sol, PlanificationProblemInput input, Instant reloj) {
        double R = calcularResponsiveness(sol, input);
        // Normalizar: fitness = 1 / (1 + R/n_pedidos)
        int n = input.getPedidos().size();
        return n > 0 ? 1.0 / (1.0 + R / n) : 0.0;
    }

    // =======================================================================
    //  Solucion inicial greedy (FIFO)
    // =======================================================================
    private static PlanificationSolutionOutput ConstruirSolucionInicial_FIFO(
            PlanificationProblemInput input, Instant reloj) {

        List<Asignacion> asignaciones = new ArrayList<>();
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
        Logger.info("Solucion inicial FIFO: rutas construidas");
        return rutaTemp.aPlanificationSolution();
    }

    // =======================================================================
    //  Metodos auxiliares del algoritmo
    // =======================================================================

    private static boolean existeClienteAlcanzable(Ruta ruta, PlanificationProblemInput input) {
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

    private static boolean existeVueloAlcanzable(Ruta ruta, PlanificationProblemInput input, Instant reloj) {
        return existeClienteAlcanzable(ruta, input);
    }

    private static Pedido SeleccionarPedidoActivo(Ruta ruta, PlanificationProblemInput input) {
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

    // Para compatibilidad con llamadas existentes
    private static boolean isPedidoActivo(Pedido p, Ruta ruta) {
        return !ruta.getUbicacionActual(p).equals(p.getDestino());
    }

    private static Pedido SeleccionarSiguientePedidoActivo(Ruta ruta, PlanificationProblemInput input) {
        return SeleccionarPedidoActivo(ruta, input);
    }

    private static boolean hayPedidosIncompletos(Ruta ruta, PlanificationProblemInput input) {
        for (Pedido p : input.getPedidos()) {
            if (!ruta.getUbicacionActual(p).equals(p.getDestino())) return true;
        }
        return false;
    }

    private static boolean pedidosNoAsignados(Ruta ruta, PlanificationProblemInput input) {
        return hayPedidosIncompletos(ruta, input);
    }

    private static boolean EsSolucionCompleta(Ruta ruta, PlanificationProblemInput input) {
        return !hayPedidosIncompletos(ruta, input);
    }

    private static Ruta IntentarInsercion(Ruta ruta, PlanificationProblemInput input) {
        for (Pedido p : input.getPedidos()) {
            if (ruta.getUbicacionActual(p).equals(p.getDestino())) continue;
            String desde = ruta.getUbicacionActual(p);
            for (Vuelo v : input.getVuelosDesdeLinea(desde)) {
                if (v.getDestino().equals(p.getDestino())
                        && v.getCapacidad() - ruta.getOcupacionVuelo(v.getId()) >= p.getCantidadMaletas()) {
                    ruta.agregarAsignacion(p, v);
                    break;
                }
            }
        }
        return ruta;
    }

    private static Ruta BusquedaLocalCROSS(Ruta ruta, PlanificationProblemInput input) {
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

    /**
     * Insercion multi-hop (BFS hasta MAX_SALTOS) para pedidos incompletos.
     * Mucho mas efectiva que buscar solo vuelo directo.
     */
    private static Ruta IntentarInsercionMultiHop(Ruta ruta, PlanificationProblemInput input) {
        final int MAX_SALTOS = 5;
        for (Pedido p : input.getPedidos()) {
            if (ruta.getUbicacionActual(p).equals(p.getDestino())) continue;

            // BFS: buscar camino de hasta MAX_SALTOS vuelos desde ubicacion actual hasta destino
            String origen = ruta.getUbicacionActual(p);
            String destino = p.getDestino();

            // BFS queue: cada entrada es la secuencia de vuelos a insertar
            Queue<List<Vuelo>> cola = new LinkedList<>();
            cola.add(new ArrayList<>());
            Set<String> visitados = new HashSet<>();
            visitados.add(origen);
            boolean encontrado = false;

            bfsLoop:
            while (!cola.isEmpty()) {
                List<Vuelo> camino = cola.poll();
                if (camino.size() >= MAX_SALTOS) continue;
                String desde = camino.isEmpty() ? origen
                        : camino.get(camino.size() - 1).getDestino();

                for (Vuelo v : input.getVuelosDesdeLinea(desde)) {
                    if (visitados.contains(v.getDestino())) continue;
                    if (v.getCapacidad() - ruta.getOcupacionVuelo(v.getId()) < p.getCantidadMaletas()) continue;

                    List<Vuelo> nuevoCamino = new ArrayList<>(camino);
                    nuevoCamino.add(v);

                    if (v.getDestino().equals(destino)) {
                        // Insertar todos los vuelos del camino
                        for (Vuelo vInsert : nuevoCamino) {
                            ruta.agregarAsignacion(p, vInsert);
                        }
                        encontrado = true;
                        break bfsLoop;
                    }
                    visitados.add(v.getDestino());
                    cola.add(nuevoCamino);
                }
            }
        }
        return ruta;
    }

    /** Cuenta cuantos pedidos llegaron efectivamente a su destino (en una Ruta activa). */
    private static int contarPedidosCompletos(Ruta ruta, PlanificationProblemInput input) {
        int count = 0;
        for (Pedido p : input.getPedidos()) {
            if (ruta.getUbicacionActual(p).equals(p.getDestino())) count++;
        }
        return count;
    }

    /** Cuenta cuantos pedidos estan en su destino segun una PlanificationSolutionOutput (sin construir Ruta). */
    private static int contarPedidosCompletosEnSol(PlanificationSolutionOutput sol, PlanificationProblemInput input) {
        if (sol == null) return 0;
        Map<String, String> ubicFinal = new HashMap<>();
        for (Asignacion a : sol.getAsignaciones()) {
            ubicFinal.put(a.getPedido().getId(), a.getVuelo().getDestino());
        }
        int count = 0;
        for (Pedido p : input.getPedidos()) {
            String uf = ubicFinal.getOrDefault(p.getId(), p.getOrigen());
            if (uf.equals(p.getDestino())) count++;
        }
        return count;
    }

    /** Cuenta cuantos pedidos tienen al menos 1 vuelo disponible desde su aeropuerto de origen. */
    private static int contarPedidosRuteables(PlanificationProblemInput input) {
        int count = 0;
        for (Pedido p : input.getPedidos()) {
            List<Vuelo> v = input.getVuelosDesdeLinea(p.getOrigen());
            if (v != null && !v.isEmpty()) count++;
        }
        return count;
    }


    // =======================================================================
    //  Utility
    // =======================================================================
    private static double duracionHorasUTC(Vuelo v, PlanificationProblemInput input) {
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

