# Contexto del Problema: Planificador de Envíos de Equipaje mediante Sistema de Colonia de Hormigas (ACS)

## Descripción General del Caso (B2B TASF)

El problema principal consiste en optimizar y planificar la distribución logística de equipajes y envíos a través de una compleja red de vuelos a escala global. El sistema en cuestión se enmarca dentro de un modelo B2B, donde existen "pedidos" (o envíos representados por conjuntos de maletas) que deben ser trasladados desde un *Aeropuerto de Origen* hasta un *Aeropuerto de Destino*.

Debido al abrumador volumen de vuelos mundiales, capacidades intermitentes y plazos de entrega estrictos, **es computacionalmente inmanejable utilizar enfoques exactos (fuerza bruta o programación lineal completa)** para planificar toda la red a la vez. Las aerolíneas sufren cancelaciones dinámicas, retrasos y sobrecuotas de capacidad. 

Por consiguiente, el sistema desarrollado emplea un **algoritmo metaheurístico de inteligencia de enjambre conocido como Sistema de Colonia de Hormigas (Ant Colony System - ACS)**. Este algoritmo busca una *optimización aproximada y eficiente en memoria* para lograr el ruteo dinámico de dichos envíos, maximizando las métricas de éxito del negocio.

## Limitaciones y Reglas del Negocio

1. **Ventanas Móviles de Planificación (Rolling Windows):**
   Para mitigar la carga de procesamiento, el simulador central (`Main.java`) gestiona los cronogramas en ventanas sucesivas de **5 días**. Toma de la base de datos de envíos únicamente aquellos pedidos cuya fecha de creación se sitúa dentro de la ventana de tiempo activa, previniendo el procesamiento redundante de pedidos muy lejanos.

2. **Ventanas de Entrega (Deadlines):**
   Las fechas límite de cumplimiento ("On Time Delivery") dependen de la geografía del envío:
   - **Mismo continente:** El envío de maletas debe completarse en máximo **1 día (24 horas)**.
   - **Continentes distintos:** El envío de maletas debe completarse en un plazo no mayor a **2 días (48 horas)**.
   Si las maletas llegan después de este límite temporal, castigan drásticamente el desempeño global de la planificación.

3. **Restricciones de Vuelo:**
   Cada vuelo posee una *capacidad máxima de maletas* limitante. Los pedidos se dividen o asignan basándose en que el volumen de sus maletas no sobrepase la capacidad libre que posee un pasaje específico.
   
## Función del Algoritmo (Cómo Opera el ACS bajo el capó)

El núcleo del programa toma su inteligencia de `AntColonySystem.java` y realiza las siguientes acciones exhaustivas durante su ciclo de vida:

### Fase 1: Inicialización
- El algoritmo inicia con una solución avariciosa (*Greedy - FIFO*) que sirve de cimiento de bajo coste para calcular una "Responsividad" inicial ($R_0$).
- Basado en esta solución básica, el programa deposita una dosis de feromona inicial uniforme en todos los vértices del grafo.
- **Manejo de Memoria Optimizada:** Dado que la interconexión entre "Miles de Pedidos" y "Miles de Vuelos" generaría una matriz de $N \times M$ insostenible de cargar en RAM, el equipo implementó un formato de **Matriz Dispersa (Sparse Matrix)**. Emplea un `ConcurrentHashMap` donde las claves son asociaciones `"id_pedido-id_vuelo"`. Solamente se guardan en memoria los bordes temporalmente explorados, evitando desbordamientos térmicos o `OutOfMemory Errors`.

### Fase 2: Construcción de Rutas (Bucle Principal)
Se determinan un conjunto de *hormigas artificiales* (ej. 10 hormigas por ciclo). Cada hormiga intentará armar un itinerario completo evaluando opciones hasta un límite máximo de ejecución (ej: 5 minutos).
1. Si existen más pedidos por ser ingresados, la hormiga busca iterativamente el vuelo siguiente más factible.
2. Utiliza una regla de selección probabilística (Regla estocástica que sopesa heurísticas contra concentración de Feromonas).
3. **Actualización de Feromona Local:** A medida que la hormiga escoge un vuelo específico, extrae un poco de feromona del camino (`tau_ij = (1 - rho) * tau_ij + rho * tau0`, con una tasa de evaporación $\rho = 0.1$). Al disipar el margen de feromonas en esa ruta, estimula a que *otras hormigas subsecuentes exploren rutas distintas*.

### Fase 3: Post-procesamiento y Evaluación
Una vez una hormiga termina un ciclo de asignaciones, su ruta puede ser pulida.
- **Heurística de Inserción:** Intenta reconectar la carga no asignada.
- **Búsqueda Local Simple (CROSS):** Ejecuta optimizaciones superficiales del plan final.

Se somete esta ruta completa al criterio de evaluación del Negocio (`CalcularFitness_ACS`), la cual puntúa la solución final usando pesos ponderados de acuerdo al grado de importancia:
*   **$W_{TIEMPO}$ (50%) - Entregas A Tiempo:** Mide numéricamente cuántas maletas cumplieron exitosamente la llegada en sus respectivos destinos antes de su fecha límite (calculada en base a su regla por continentes).
*   **$W_{EFIC}$ (30%) - Eficiencia Operativa:** Verifica el promedio de uso o nivel de carga estibada en un vuelo vs un humbral estático. Castiga enviar vuelos "Casi vacíos".
*   **$W_{RIESGO}$ (20%) - Control de Riesgo:** Penaliza altamente la saturación. Resta puntaje si un vuelo viaja a un $100\%$ peligroso de capacidad, impidiendo maniobrabilidad en caso de cancelaciones; lo mismo sobre la saturación del espacio físico del aeropuerto (buffers de terminal).

### Fase 4: Actualización Global
- Si el plan resultante de la hormiga tiene un *Fitness* superior al mejor guardado hasta la fecha, **se le corona como el nuevo estándar global**.
- A los enlaces `Pedido-Vuelo` dictaminados por ese plan ganador absoluto se les vierte una **fuerte cantidad de Feromona Global** positivamente correlacionada a su buen puntaje general ($1 / Fitness$).

## Registros y Trazabilidad (Logging System)
Una vez que el ACS termina de deliberar todos los movimientos logísticos rentables (y desecha los infactibles) en su asignación de 5 días, finaliza reportando al exterior en un minucioso documento de auditoría llamado `log_planificacion.txt`.

El sistema está condicionado a entregar una respuesta definitiva para cada pedido e incluye:
- **Trazabilidad Exitosa Directa:** Confirmación cuando el pedido tiene un despegue e inmediata llegada a su destino.
- **Vuelos con Escalas / Conexiones:** Desglose del "paso a paso" temporal (`Vuelo X -> Vuelo Y -> Vuelo Z`) donde el equipaje hizo transbordos con sus respectivas horas de salida y llegada estimadas.
- **Detectores de Fallas (Envíos Bloqueados / Aislados):** Una confirmación rigurosa si un envío logró completar una ruta de vuelo hacia el exterior pero quedó bloqueado a la mitad en caso de haber un vuelo cancelado u obsoleto y no le es posible tocar su terminal de destino. Se marca como éxito Parcial/Fallido y notifica el punto de estancamiento.
