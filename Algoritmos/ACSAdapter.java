import java.time.LocalDateTime;
import java.util.*;

/**
 * Adaptador entre el ACS (que internamente trabaja con sus propias clases Aeropuerto,
 * Vuelo, Pedido) y las estructuras canónicas del AG (AeropuertoAlgoritmo,
 * VueloAlgoritmo, EnvioAlgoritmo, ResultadoRuta).
 *
 * Expone un único método estático:
 *   planificar(PlanificationProblemInput, long tiempoMs) -> PlanificationSolutionOutput
 *
 * Internamente convierte los datos, ejecuta ACS_TASF y traduce el resultado.
 */
public class ACSAdapter {

    /**
     * Ejecuta el ACS sobre el sub-input dado y devuelve la solución
     * en el formato canónico del proyecto.
     *
     * @param input    Sub-input con aeropuertos, vuelos y envíos del bloque
     * @param tiempoMs Tiempo máximo de ejecución del ACS en milisegundos
     * @return         Solución con rutas, capacidades y métricas
     */
    public static PlanificationSolutionOutput planificar(PlanificationProblemInput input, long tiempoMs) {

        PlanificationSolutionOutput output = new PlanificationSolutionOutput("ACS");

        if (input.getEnvios().isEmpty()) {
            output.setMetricaCalidad(0.0);
            return output;
        }

        // --- 1. Construir AeropuertoAlgoritmo → Aeropuerto (ACS interno) ---
        Map<String, Aeropuerto> mapaAeropuertosACS = new HashMap<>();
        for (AeropuertoAlgoritmo aa : input.getMapaAeropuertos().values()) {
            Aeropuerto a = new Aeropuerto(
                    aa.getOaci(),
                    aa.getContinente(),
                    aa.getGmt(),
                    aa.getCapacidadAlmacen()
            );
            mapaAeropuertosACS.put(aa.getOaci(), a);
        }

        // --- 2. Construir VueloAlgoritmo → Vuelo (ACS interno) ---
        // También mapa inverso Vuelo.id -> VueloAlgoritmo para traducción de vuelta
        Map<String, VueloAlgoritmo> mapaVueloPorId = new HashMap<>();
        List<Vuelo> vuelosACS = new ArrayList<>();
        for (VueloAlgoritmo va : input.getTodosLosVuelos()) {
            Vuelo v = new Vuelo(
                    va.getOrigenOaci(),
                    va.getDestinoOaci(),
                    va.getHoraSalida(),
                    va.getHoraLlegada(),
                    va.getCapacidad()
            );
            vuelosACS.add(v);
            mapaVueloPorId.put(v.getId(), va);
        }

        // --- 3. Construir EnvioAlgoritmo → Pedido (ACS interno) ---
        // También mapa inverso Pedido.id -> EnvioAlgoritmo
        Map<String, EnvioAlgoritmo> mapaEnvioPorPedidoId = new HashMap<>();
        List<Pedido> pedidosACS = new ArrayList<>();
        for (EnvioAlgoritmo ea : input.getEnvios()) {
            // Determinar límite temporal (24h mismo continente, 48h distinto)
            AeropuertoAlgoritmo aOrig  = input.getAeropuerto(ea.getOrigenOaci());
            AeropuertoAlgoritmo aDest  = input.getAeropuerto(ea.getDestinoOaci());
            int limiteHoras = 48;
            if (aOrig != null && aDest != null &&
                    aOrig.getContinente().equalsIgnoreCase(aDest.getContinente())) {
                limiteHoras = 24;
            }

            // ID único = origen + "-" + idEnvio (igual que en GA)
            String idUnico = ea.getOrigenOaci() + "-" + ea.getId();
            Pedido p = new Pedido(
                    idUnico,
                    ea.getOrigenOaci(),
                    ea.getDestinoOaci(),
                    ea.getFechaHoraRegistro(),
                    ea.getCantidadMaletas(),
                    ea.getClienteId()
            );
            p.setTiempoLimite(ea.getFechaHoraRegistro().plusHours(limiteHoras));
            pedidosACS.add(p);
            mapaEnvioPorPedidoId.put(idUnico, ea);
        }

        // --- 4. Construir PlanificationProblemInput ACS interno ---
        PlanificationProblemInputACS inputACS = new PlanificationProblemInputACS(
                mapaAeropuertosACS, vuelosACS, pedidosACS,
                input.getOcupacionGlobalVuelos()   // compartir ocupación acumulada
        );

        // --- 5. Ejecutar ACS ---
        PlanificationSolutionOutputACS solACS =
                AntColonySystem.ACS_TASF(inputACS, java.time.Instant.now(), tiempoMs);

        // --- 6. Traducir resultado ACS → formato canónico AG ---
        // Reconstruimos rutas para cada envío a partir de las asignaciones ACS
        Map<String, List<Asignacion>> asigPorPedido = new LinkedHashMap<>();
        for (Asignacion asig : solACS.getAsignaciones()) {
            asigPorPedido
                    .computeIfAbsent(asig.getPedido().getId(), k -> new ArrayList<>())
                    .add(asig);
        }

        // Estado de capacidades y almacenes para propagar al input global
        Map<String, Integer> capVuelos   = new HashMap<>(input.getOcupacionGlobalVuelos());
        Map<String, Integer> capAlmacenes = new HashMap<>(input.getOcupacionGlobalAlmacenes());

        for (Map.Entry<String, List<Asignacion>> entry : asigPorPedido.entrySet()) {
            String pedidoId = entry.getKey();
            EnvioAlgoritmo envio = mapaEnvioPorPedidoId.get(pedidoId);
            if (envio == null) continue;

            List<Asignacion> asigs = entry.getValue();

            // Reconstruir tiempos de cada vuelo (igual que VueloSelector.getDisponibilidadAbsoluta)
            List<VueloAlgoritmo> vuelosUsados = new ArrayList<>();
            List<LocalDateTime>  fechasVuelo  = new ArrayList<>();

            LocalDateTime tiempoActual = envio.getFechaHoraRegistro();
            LocalDateTime llegadaFinal = tiempoActual;

            for (Asignacion a : asigs) {
                VueloAlgoritmo va = mapaVueloPorId.get(a.getVuelo().getId());
                if (va == null) continue;

                LocalDateTime salida = tiempoActual.with(va.getHoraSalida());
                if (salida.isBefore(tiempoActual)) salida = salida.plusDays(1);

                LocalDateTime llegada = salida.with(va.getHoraLlegada());
                if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

                vuelosUsados.add(va);
                fechasVuelo.add(salida);

                // Actualizar ocupación global de vuelo
                String claveVuelo = va.getOrigenOaci() + "-" + va.getDestinoOaci()
                        + "-" + va.getHoraSalida() + "-" + salida.toLocalDate();
                capVuelos.merge(claveVuelo, envio.getCantidadMaletas(), Integer::sum);
                input.incrementarOcupacionGlobalVuelo(claveVuelo, envio.getCantidadMaletas());

                llegadaFinal  = llegada;
                tiempoActual  = llegada; // Sin +10min: consistente con VueloSelector.getDisponibilidadAbsoluta
            }

            if (!vuelosUsados.isEmpty()) {
                ResultadoRuta ruta = new ResultadoRuta(llegadaFinal, vuelosUsados, fechasVuelo);
                output.agregarRuta(envio, ruta);
            }
        }

        output.calcularMetricaUnificada();
        output.setEstadoCapacidadesVuelos(capVuelos);
        output.setEstadoOcupacionAlmacenes(capAlmacenes);

        return output;
    }
}
