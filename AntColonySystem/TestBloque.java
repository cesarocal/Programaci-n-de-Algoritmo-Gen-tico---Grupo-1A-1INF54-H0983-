import java.time.*;
import java.util.*;

/** Test rapido: correr ACS con un bloque de pedidos para verificar que produce asignaciones. */
public class TestBloque {
    public static void main(String[] args) {
        Logger.init();
        PlanificationProblemInput input = new PlanificationProblemInput();
        LectorArchivos.cargarAeropuertos(input);
        LectorArchivos.cargarVuelos(input);

        LocalDateTime inicio = LectorArchivos.detectarFechaInicio();
        LectorArchivos.cargarEnvios(input, inicio, inicio.plusDays(5));

        // Tomar solo los primeros 20 pedidos
        List<Pedido> muestra = new ArrayList<>(input.getPedidos().subList(0, Math.min(20, input.getPedidos().size())));
        PlanificationProblemInput subInput = input.crearSubInput(muestra);

        System.out.println("=== TEST BLOQUE ===");
        System.out.println("Pedidos: " + subInput.getPedidos().size());

        // Mostrar origenes y vuelos disponibles
        for (Pedido p : muestra) {
            List<Vuelo> v = subInput.getVuelosDesdeLinea(p.getOrigen());
            int directos = 0;
            for (Vuelo vv : v) {
                if (vv.getDestino().equals(p.getDestino())) directos++;
            }
            System.out.println("  Pedido " + p.getId() + ": " + p.getOrigen() + "->" + p.getDestino()
                    + "  vuelos_desde_origen=" + v.size() + "  directos=" + directos);
        }

        // Ejecutar ACS con 3 segundos
        System.out.println("\nEjecutando ACS 3s...");
        PlanificationSolutionOutput sol = AntColonySystem.ACS_TASF(subInput, Instant.now(), 3000);
        System.out.println("Asignaciones: " + sol.getAsignaciones().size());

        // Verificar completados
        Map<String, String> ubicFinal = new HashMap<>();
        for (Asignacion a : sol.getAsignaciones()) {
            ubicFinal.put(a.getPedido().getId(), a.getVuelo().getDestino());
        }
        int ok = 0;
        for (Pedido p : muestra) {
            String uf = ubicFinal.getOrDefault(p.getId(), p.getOrigen());
            boolean llego = uf.equals(p.getDestino());
            if (llego) ok++;
            System.out.println("  " + p.getId() + ": " + p.getOrigen() + "->" + uf
                    + (llego ? " OK" : " INCOMPLETO (destino=" + p.getDestino() + ")"));
        }
        System.out.println("Completados: " + ok + "/" + muestra.size());
        Logger.close();
    }
}
