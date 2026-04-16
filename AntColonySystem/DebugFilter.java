import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DebugFilter {
    public static void main(String[] args) {
        Logger.init();
        PlanificationProblemInput inputMaestro = new PlanificationProblemInput();
        LectorArchivos.cargarAeropuertos(inputMaestro);
        LectorArchivos.cargarVuelos(inputMaestro);

        LocalDateTime fechaInicio = LectorArchivos.detectarFechaInicio();
        LocalDateTime fechaFin = fechaInicio.plusDays(5);
        LectorArchivos.cargarEnvios(inputMaestro, fechaInicio, fechaFin);

        List<Pedido> todosLosPedidos = inputMaestro.getPedidos();
        Set<String> pedidosCompletados = new HashSet<>();
        
        System.out.println("Total loaded: " + todosLosPedidos.size());
        
        Set<String> uniqueIds = new HashSet<>();
        for(Pedido p : todosLosPedidos) uniqueIds.add(p.getId());
        System.out.println("Unique IDs: " + uniqueIds.size());

        LocalDateTime cursor = fechaInicio;
        int Sc_MINUTOS = 240;
        int Sa_MINUTOS = 40;

        for (int bloque = 1; bloque <= 3; bloque++) {
            LocalDateTime ventanaInicio = cursor;
            LocalDateTime ventanaFin = cursor.plusMinutes(Sc_MINUTOS);
            LocalDateTime limiteAnterior = ventanaInicio.minusMinutes(Sc_MINUTOS);
            
            System.out.println("\nBloque " + bloque + " Window: " + limiteAnterior + " to " + ventanaFin);
            int validos = 0;

            for (Pedido p : todosLosPedidos) {
                if (pedidosCompletados.contains(p.getId())) continue;
                LocalDateTime tc = p.getTiempoCreacion();
                if (!tc.isBefore(limiteAnterior) && tc.isBefore(ventanaFin)) {
                    validos++;
                    if(bloque == 1 && validos <= 5) {
                         // fake complete 5 items in block 1
                         pedidosCompletados.add(p.getId());
                    }
                }
            }
            System.out.println("Pedidos validos en bloque: " + validos);
            cursor = cursor.plusMinutes(Sa_MINUTOS);
        }
        
        Logger.close();
    }
}
