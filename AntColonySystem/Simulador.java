import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;

/**
 * Simulador con Planificacion por Ventana Deslizante (Rolling Horizon).
 *
 * Parametros:
 *   Ta  = Tiempo de ejecucion del ACS por bloque (ms)
 *   Sa  = Salto simulado entre ejecuciones (min)
 *   K   = Constante de proporcionalidad
 *   Sc  = K * Sa = ventana de consumo de datos (min)
 */
public class Simulador {

    // ===== PARAMETROS ADAPTABLES =====
    private static final long   Ta          = 15_000;  // 15 segundos por bloque
    private static final int    Sa_MINUTOS  = 40;      // salto entre ejecuciones
    private static final int    K           = 6;       // constante de proporcionalidad
    private static final int    Sc_MINUTOS  = K * Sa_MINUTOS; // 240 min = 4 horas
    private static final int    NUM_BLOQUES = 180;     // 180 * 40min = 5 dias

    public static void iniciar(String parametroFecha) {
        Logger.init();
        Logger.info("=== INICIANDO NUEVA EJECUCION ===");
        System.out.println("======================================================");
        System.out.println("  Simulador ACS - Ventana Deslizante");
        System.out.println("======================================================");
        System.out.println("  Ta=" + (Ta/1000) + "s | Sa=" + Sa_MINUTOS + "min | K=" + K
                + " | Sc=" + Sc_MINUTOS + "min | Bloques=" + NUM_BLOQUES);
        System.out.println("  Tiempo total estimado: ~" + (NUM_BLOQUES * Ta / 1000) + "s");
        System.out.println("======================================================");

        // Cargar datos maestros
        PlanificationProblemInput inputMaestro = new PlanificationProblemInput();
        System.out.println(">> Cargando aeropuertos y vuelos...");
        LectorArchivos.cargarAeropuertos(inputMaestro);
        LectorArchivos.cargarVuelos(inputMaestro);

        // Detectar fecha inicio
        LocalDateTime fechaInicio = detectarFecha(parametroFecha);
        LocalDateTime fechaFin = fechaInicio.plusDays(5);

        System.out.println(">> Cargando envios [" + fechaInicio + " - " + fechaFin + "]...");
        LectorArchivos.cargarEnvios(inputMaestro, fechaInicio, fechaFin);

        List<Pedido> todosLosPedidos = new ArrayList<>(inputMaestro.getPedidos());
        System.out.println(">> Total pedidos: " + todosLosPedidos.size());

        // --- Diagnostico rapido ---
        int conVuelos = 0;
        for (Pedido p : todosLosPedidos) {
            List<Vuelo> v = inputMaestro.getVuelosDesdeLinea(p.getOrigen());
            if (v != null && !v.isEmpty()) conVuelos++;
        }
        System.out.println(">> Pedidos con vuelos desde su origen: " + conVuelos + "/" + todosLosPedidos.size());
        Logger.info("Pedidos con vuelos desde origen: " + conVuelos + "/" + todosLosPedidos.size());

        // --- Ejecutar ---
        ejecutarPorBloques(inputMaestro, todosLosPedidos, fechaInicio, fechaFin);
    }

    private static void ejecutarPorBloques(
            PlanificationProblemInput inputMaestro,
            List<Pedido> todosLosPedidos,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin) {

        Set<String>      pedidosCompletados  = new HashSet<>();
        List<Asignacion> asignacionesGlobales = Collections.synchronizedList(new ArrayList<>());
        LocalDateTime    cursor              = fechaInicio;
        Instant          reloj               = Instant.now();
        long             tiempoTotalReal     = System.currentTimeMillis();
        int              pedidosTotales       = todosLosPedidos.size();

        // SHUTDOWN HOOK para generar el log si el usuario cancela con Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[AVISO] Guardando log parcial antes de salir...");
            PlanificationSolutionOutput tempSol = new PlanificationSolutionOutput(asignacionesGlobales);
            generarLogDeEnvios(todosLosPedidos, tempSol, fechaInicio, fechaFin);
        }));

        System.out.println("\n>> Iniciando planificacion por bloques...\n");

        for (int bloque = 1; bloque <= NUM_BLOQUES; bloque++) {
            LocalDateTime ventanaInicio = cursor;
            LocalDateTime ventanaFin    = cursor.plusMinutes(Sc_MINUTOS);

            // 1. Filtrar pedidos para este bloque:
            //    - tiempoCreacion en [ventanaInicio - Sc, ventanaFin)
            //      Esto da a cada pedido hasta 2*Sc minutos (8h) para ser ruteado
            //    - NO completado en bloques anteriores
            LocalDateTime limiteAnterior = ventanaInicio.minusMinutes(Sc_MINUTOS);
            List<Pedido> pedidosBloque = new ArrayList<>();
            for (Pedido p : todosLosPedidos) {
                if (pedidosCompletados.contains(p.getId())) continue;
                LocalDateTime tc = p.getTiempoCreacion();
                if (!tc.isBefore(limiteAnterior) && tc.isBefore(ventanaFin)) {
                    pedidosBloque.add(p);
                }
            }

            if (pedidosBloque.isEmpty()) {
                cursor = cursor.plusMinutes(Sa_MINUTOS);
                continue;
            }

            // 2. Crear sub-input compartiendo vuelos y aeropuertos (sin copiar)
            PlanificationProblemInput subInput = inputMaestro.crearSubInput(pedidosBloque);

            // 3. Ejecutar ACS
            long t0 = System.currentTimeMillis();
            PlanificationSolutionOutput resultado = AntColonySystem.ACS_TASF(subInput, reloj, Ta);
            long duracion = System.currentTimeMillis() - t0;

            // 4. Consolidar: determinar ubicacion final de cada pedido en este bloque
            int completadosEnBloque = 0;
            Map<String, String> ubicFinal = new HashMap<>();
            for (Asignacion a : resultado.getAsignaciones()) {
                // El ultimo vuelo asignado determina la ubicacion final
                ubicFinal.put(a.getPedido().getId(), a.getVuelo().getDestino());
            }
            for (Pedido p : pedidosBloque) {
                if (pedidosCompletados.contains(p.getId())) continue; // evitar doble conteo
                String uf = ubicFinal.getOrDefault(p.getId(), p.getOrigen());
                if (uf.equals(p.getDestino())) {
                    pedidosCompletados.add(p.getId());
                    completadosEnBloque++;
                }
            }

            for (Asignacion a : resultado.getAsignaciones()) {
                inputMaestro.incrementarOcupacionGlobal(a.getFlightKey(), a.getPedido().getCantidadMaletas());
            }
            asignacionesGlobales.addAll(resultado.getAsignaciones());

            // Log
            String msg = String.format(
                    "[Bloque %3d/%d] %s-%s | Pedidos:%d | Asig:%d | OK:%d | Acum:%d/%d | %ds",
                    bloque, NUM_BLOQUES,
                    ventanaInicio.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    ventanaFin.format(DateTimeFormatter.ofPattern("HH:mm")),
                    pedidosBloque.size(),
                    resultado.getAsignaciones().size(),
                    completadosEnBloque,
                    pedidosCompletados.size(), pedidosTotales,
                    duracion / 1000);
            System.out.println(msg);
            // Generar log progresivo en disco por si el usuario cancela a la mitad
            PlanificationSolutionOutput progresivo = new PlanificationSolutionOutput(asignacionesGlobales);
            generarLogDeEnvios(todosLosPedidos, progresivo, fechaInicio, fechaFin);

            // 5. Avanzar
            cursor = cursor.plusMinutes(Sa_MINUTOS);
        }

        long tiempoTotal = (System.currentTimeMillis() - tiempoTotalReal) / 1000;

        System.out.println("\n======================================================");
        System.out.println("  PLANIFICACION COMPLETADA");
        System.out.println("  Completados: " + pedidosCompletados.size() + "/" + pedidosTotales
                + " (" + String.format("%.1f", 100.0 * pedidosCompletados.size() / Math.max(1, pedidosTotales)) + "%)");
        System.out.println("  Asignaciones: " + asignacionesGlobales.size());
        System.out.println("  Tiempo total: " + tiempoTotal + "s");
        System.out.println("======================================================");

        Logger.info("Completados: " + pedidosCompletados.size() + "/" + pedidosTotales
                + " Asignaciones: " + asignacionesGlobales.size() + " Tiempo: " + tiempoTotal + "s");

        PlanificationSolutionOutput solucionFinal = new PlanificationSolutionOutput(asignacionesGlobales);
        generarLogDeEnvios(todosLosPedidos, solucionFinal, fechaInicio, fechaFin);
        Logger.info("=== SIMULACION FINALIZADA ===");
        Logger.close();
    }

    // ======= Helpers =======

    private static LocalDateTime detectarFecha(String param) {
        if (param != null) {
            try { return LocalDateTime.parse(param + "T00:00:00"); }
            catch (Exception e) { System.out.println("Formato invalido. Auto-detectando..."); }
        }
        return LectorArchivos.detectarFechaInicio();
    }

    private static void generarLogDeEnvios(List<Pedido> pedidos, PlanificationSolutionOutput solucion,
                                            LocalDateTime inicio, LocalDateTime fin) {
        // System.out.println(">> Generando 'log_planificacion.txt'...");

        Map<String, List<Vuelo>> vuelosPorPedido = new LinkedHashMap<>();
        Map<String, Integer> ocupacionVuelos = new HashMap<>(); // Tracking the usage of each flight
        
        for (Asignacion asig : solucion.getAsignaciones()) {
            vuelosPorPedido.computeIfAbsent(asig.getPedido().getId(), k -> new ArrayList<>())
                    .add(asig.getVuelo());
            // Sumar la ocupacion global del vuelo usando su ID
            String vId = asig.getVuelo().getId();
            ocupacionVuelos.put(vId, ocupacionVuelos.getOrDefault(vId, 0) + asig.getPedido().getCantidadMaletas());
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter("log_planificacion.txt"))) {
            writer.println("==================================================");
            writer.println("   REPORTE DE PLANIFICACION - VENTANA DESLIZANTE");
            writer.println("==================================================");
            writer.println("Periodo: " + inicio.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    + " al " + fin.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            writer.println("Parametros: Ta=" + (Ta/1000) + "s  Sa=" + Sa_MINUTOS + "min  K=" + K
                    + "  Sc=" + Sc_MINUTOS + "min  Bloques=" + NUM_BLOQUES);
            writer.println("Total pedidos: " + pedidos.size());
            writer.println("==================================================\n");

            int completados = 0, fallidos = 0;

            for (Pedido p : pedidos) {
                writer.println("--- ENVIO ID: " + p.getId() + " ---");
                writer.println("Origen: " + p.getOrigen() + " | Destino: " + p.getDestino()
                        + " | Maletas: " + p.getCantidadMaletas()
                        + " | Creado: " + p.getTiempoCreacion().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));

                List<Vuelo> vuelos = vuelosPorPedido.get(p.getId());

                if (vuelos == null || vuelos.isEmpty()) {
                    writer.println("ESTADO: FALLIDO - Sin vuelos asignados.");
                    fallidos++;
                } else {
                    String destFinal = vuelos.get(vuelos.size() - 1).getDestino();
                    boolean ok = destFinal.equals(p.getDestino());

                    if (ok) {
                        completados++;
                        writer.println("ESTADO: EXITO" + (vuelos.size() == 1 ? " (Directo)" : " (" + (vuelos.size()-1) + " escalas)"));
                    } else {
                        fallidos++;
                        writer.println("ESTADO: PARCIAL - Bloqueado en " + destFinal);
                    }

                    writer.println("RUTA DETALLADA:");
                    for (int i = 0; i < vuelos.size(); i++) {
                        Vuelo v = vuelos.get(i);
                        boolean esUltimo = (i == vuelos.size() - 1);
                        boolean llegoDestinoReal = esUltimo && v.getDestino().equals(p.getDestino());
                        String estadoPaso = llegoDestinoReal ? "LLEGO A DESTINO" : "EN ESCALA";
                        int usoVuelo = ocupacionVuelos.getOrDefault(v.getId(), 0);
                        
                        writer.println("  Paso " + (i+1) + ":");
                        writer.println("    Vuelo     : [" + v.getId() + "] " + v.getOrigen() + " -> " + v.getDestino());
                        writer.println("    Horario   : " + v.getHoraSalida().format(DateTimeFormatter.ofPattern("HH:mm")) + 
                                       " hasta " + v.getHoraLlegada().format(DateTimeFormatter.ofPattern("HH:mm")));
                        writer.println("    Capacidad : Usada " + usoVuelo + " / " + v.getCapacidad() + " maletas");
                        writer.println("    Tracking  : Envio=" + p.getId() + " | Maletas=" + p.getCantidadMaletas() + 
                                       " | Origen Inicial=" + p.getOrigen() + " | Ubicacion Actual Pos-Vuelo=" + v.getDestino() + 
                                       " | Estado=" + estadoPaso);
                    }
                }
                writer.println("--------------------------------------------------\n");
            }

            writer.println("================ RESUMEN =========================");
            writer.println("Completados : " + completados + "/" + pedidos.size()
                    + " (" + String.format("%.1f", 100.0 * completados / Math.max(pedidos.size(), 1)) + "%)");
            writer.println("Fallidos    : " + fallidos);
            writer.println("==================================================");

        } catch (IOException e) {
            System.err.println("Error escribiendo log: " + e.getMessage());
        }
    }
}
