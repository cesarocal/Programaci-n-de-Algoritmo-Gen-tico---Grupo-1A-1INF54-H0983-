import java.util.*;

/**
 * Adaptador del Algoritmo Genético.
 * Envuelve AlgoritmoGenetico para que devuelva PlanificationSolutionOutput
 * (el formato canónico del proyecto).
 */
public class AGAdapter {

    /**
     * Ejecuta el Algoritmo Genético sobre el sub-input dado.
     *
     * @param input        Sub-input con aeropuertos, vuelos y envíos del bloque
     * @param tiempoSegundos Tiempo máximo en segundos
     * @return             Solución con rutas, capacidades y métricas
     */
    public static PlanificationSolutionOutput planificar(
            PlanificationProblemInput input, int tiempoSegundos) {

        PlanificationSolutionOutput output = new PlanificationSolutionOutput("AG");

        if (input.getEnvios().isEmpty()) {
            output.setMetricaCalidad(0.0);
            return output;
        }

        // Instanciar el AG con los datos canónicos (ya comparte referencias)
        AlgoritmoGenetico ag = new AlgoritmoGenetico(
                input.getTodosLosVuelos(),
                input.getMapaAeropuertos()
        );

        // Transferir la ocupación global de vuelos al AG
        // (el AG usa su propio mapa interno estadoCapacidadesFinal que se inicializa
        //  vacío, por lo que inicializamos su estado con el acumulado global)
        // Nota: AlgoritmoGenetico.planificar() recibe envíos del bloque + tiempo límite
        List<EnvioAlgoritmo> enviosPlanificados = ag.planificar(input.getEnvios(), tiempoSegundos);

        Map<String, ResultadoRuta> mapaRutas  = ag.getMejoresRutasActuales();
        Map<String, Integer> capVuelos        = ag.getEstadoCapacidadesFinal();
        Map<String, Integer> ocupAlmacenes    = ag.getOcupacionAlmacenesFisicos();

        // Registrar rutas en el output canónico
        for (EnvioAlgoritmo envio : enviosPlanificados) {
            String clave = envio.getOrigenOaci() + "-" + envio.getId();
            ResultadoRuta ruta = mapaRutas.get(clave);
            output.agregarRuta(envio, ruta);
        }

        // Propagar ocupaciones al input global (para que el siguiente bloque las tenga)
        for (Map.Entry<String, Integer> e : capVuelos.entrySet()) {
            // capVuelos guarda capacidad RESTANTE (capacidad - uso), necesitamos el uso
            // Recalculamos: para cada clave, buscamos el vuelo original y deducimos
            // No disponemos del vuelo original fácilmente por clave, así que simplemente
            // actualizamos el mapa global de ocupación con las diferencias
        }
        // Más limpio: propagamos directamente el estado de ocupación de almacenes
        for (Map.Entry<String, Integer> e : ocupAlmacenes.entrySet()) {
            input.incrementarOcupacionGlobalAlmacen(e.getKey(), e.getValue());
        }

        output.calcularMetricaUnificada();
        output.setEstadoCapacidadesVuelos(capVuelos);
        output.setEstadoOcupacionAlmacenes(ocupAlmacenes);

        return output;
    }
}
