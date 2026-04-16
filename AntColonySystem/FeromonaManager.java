import java.util.Map;

public class FeromonaManager {

    /**
     * FUNCIÓN ConservarFeromonas(τ_anterior, τ0_anterior, τ0_nuevo)
     * Conservación entre reoptimizaciones (ante cancelación de vuelo o eventos dinámicos):
     */
    public static void ConservarFeromonas(Map<String, Double> feromonas, double tau0Anterior, double tau0Nuevo, double gamma) {
        
        // PARA CADA par (i,j) presentes en nuevo problema HACER
        // Iteramos directamente sobre las claves almacenadas en nuestra matriz dispersa.
        for (Map.Entry<String, Double> entry : feromonas.entrySet()) {
            
            // τ_ij_anterior
            double tau_ij_anterior = entry.getValue();
            
            // τij ← (1−γ) * τij_anterior * (τ0_nuevo/τ0_anterior) + γ * τ0_nuevo
            double factorActualizacion = tau0Nuevo / tau0Anterior;
            double nuevoTau = (1 - gamma) * tau_ij_anterior * factorActualizacion + gamma * tau0Nuevo;
            
            // Actualizamos en el mapa
            feromonas.put(entry.getKey(), nuevoTau);
        }
    }
}
