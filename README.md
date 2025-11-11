# portafolio-optimizer

## Qué hace?
Optimiza portafolios leyendo activos y matrices de correlaciones y termina generando:
- Greedy (heurística)
- Óptimo (Backtracking y Branch&Bound)
- Alternativos (heurísticas alternativas)
Valida restricciones como: monto, cardinalidad, cuotas, retorno mínimo, riesgo por perfil,etc.

## Cómo correr
Requisitos: JDK 17+
Desde la raíz del proyecto:
```bash
find src -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out app.portafolio.presentacion.Main
