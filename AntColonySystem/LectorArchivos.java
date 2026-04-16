import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LectorArchivos {

    public static LocalDateTime detectarFechaInicio() {
        File folder = new File("envios");
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("❌ Directorio 'envios' no encontrado.");
            return LocalDateTime.now();
        }
        for (File file : folder.listFiles()) {
            if (!file.getName().endsWith(".txt")) continue;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String linea = br.readLine();
                if (linea != null) {
                    String[] tokens = linea.split("-");
                    if (tokens.length >= 2) {
                        return java.time.LocalDate.parse(tokens[1], DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
                    }
                }
            } catch (Exception e) {}
        }
        return LocalDateTime.now();
    }

    public static void cargarAeropuertos(PlanificationProblemInput input) {
        String archivo = "c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt";
        File file = new File(archivo);
        if (!file.exists()) {
            System.out.println("❌ Archivo de aeropuertos no encontrado: " + archivo);
            return;
        }
        System.out.println(">> Leyendo archivo de aeropuertos...");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String linea;
            String continenteActual = "Desconocido";
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("*") || linea.startsWith("PDDS")) continue;
                if (!linea.substring(0, 1).matches("\\d")) {
                    continenteActual = linea.split("\\t")[0].trim();
                    continue;
                }
                String[] tokens = linea.split("\\s+");
                int latIdx = -1;
                for (int i=0; i<tokens.length; i++) {
                    if (tokens[i].equals("Latitude:")) latIdx = i;
                }
                if (latIdx >= 2) {
                    try {
                        int capacidad = Integer.parseInt(tokens[latIdx - 1]);
                        String husoRaw = tokens[latIdx - 2];
                        int huso = husoRaw.startsWith("+") ? Integer.parseInt(husoRaw.substring(1)) : Integer.parseInt(husoRaw);
                        Aeropuerto a = new Aeropuerto(tokens[1], continenteActual, huso, capacidad);
                        input.agregarAeropuerto(a);
                    } catch (Exception e) {}
                }
            }
            Logger.info("Archivo de aeropuertos parseado exitosamente. Capacidad insertada: " + input.getAeropuertos().size() + " nodos.");
        } catch (IOException e) {}
    }

    public static void cargarVuelos(PlanificationProblemInput input) {
        File file = new File("planes_vuelo.txt");
        if (!file.exists()) {
            System.out.println("❌ Archivo de vuelos no encontrado: planes_vuelo.txt");
            return;
        }
        System.out.println(">> Leyendo archivo de planes de vuelo...");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                String[] tokens = linea.split("-");
                if (tokens.length >= 5) {
                    try {
                        java.time.LocalTime salida = java.time.LocalTime.parse(tokens[2]);
                        java.time.LocalTime llegada = java.time.LocalTime.parse(tokens[3]);
                        int cap = Integer.parseInt(tokens[4]);
                        input.agregarVuelo(new Vuelo(tokens[0], tokens[1], salida, llegada, cap));
                    } catch (Exception e) {}
                }
            }
            Logger.info("Archivo de planes de vuelo analizado existosamente.");
        } catch (IOException e) {}
    }

    public static void cargarEnvios(PlanificationProblemInput input, LocalDateTime inicio, LocalDateTime fin) {
        File folder = new File("envios");
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("❌ Directorio 'envios' no encontrado al intentar procesar pedidos.");
            return;
        }
        DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("yyyyMMdd");
        
        File[] archivos = folder.listFiles();
        int totalTxt = 0;
        for (File f : archivos) {
            if (f.getName().endsWith(".txt")) totalTxt++;
        }
        System.out.println(">> Procesando " + totalTxt + " archivos de envíos...");
        Logger.info("Comenzando a escanear " + totalTxt + " archivos de envíos para ventana [" + inicio + " - " + fin + "]");
        
        int procesados = 0;
        for (File file : archivos) {
            if (!file.getName().endsWith(".txt")) continue;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] tokens = linea.split("-");
                    if (tokens.length >= 7) {
                        try {
                            LocalDateTime fecha = java.time.LocalDate.parse(tokens[1], formatterDate)
                                    .atTime(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]));
                            if (!fecha.isBefore(inicio) && fecha.isBefore(fin)) {
                                String origen = file.getName().replace("_envios_", "").replace("_.txt", "");
                                String destinoFinal = tokens[4];
                                // Asegurar que el ID del pedido sea unico a nivel global, ya que distintos archivos repiten tokens[0]
                                String idPedidoUnico = origen + "-" + tokens[0];
                                Pedido nuevoPedido = new Pedido(idPedidoUnico, origen, destinoFinal, fecha, Integer.parseInt(tokens[5]), tokens[6]);
                                
                                // Establecer limite de horas: 24h mismo continente, 48h distinto continente
                                Aeropuerto aeroOrigen = input.getAeropuerto(origen);
                                Aeropuerto aeroDestino = input.getAeropuerto(destinoFinal);
                                int limiteHoras = 48; // por defecto si hay error
                                if (aeroOrigen != null && aeroDestino != null) {
                                    if (aeroOrigen.getContinente().equals(aeroDestino.getContinente())) {
                                        limiteHoras = 24;
                                    }
                                }
                                nuevoPedido.setTiempoLimite(fecha.plusHours(limiteHoras));
                                
                                input.agregarPedido(nuevoPedido);
                            }
                        } catch (Exception e) {}
                    }
                }
            } catch (IOException e) {}
            procesados++;
            if (procesados % 10 == 0 || procesados == totalTxt) {
                System.out.println("   - " + procesados + "/" + totalTxt + " archivos analizados...");
                Logger.info("Progreso de escaneo de envíos: " + procesados + " de " + totalTxt + " archivos completados.");
            }
        }
        Logger.info("Escaneo finalizado. Total de pedidos filtrados y retenidos: " + input.getPedidos().size());
    }
}
