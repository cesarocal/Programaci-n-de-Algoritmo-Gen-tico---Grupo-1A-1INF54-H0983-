public class Main {
    public static void main(String[] args) {
        String parametroFecha = args.length > 0 ? args[0] : null;
        Simulador.iniciar("2026-01-01");
    }
}