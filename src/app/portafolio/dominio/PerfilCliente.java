package app.portafolio.dominio;

public enum PerfilCliente {
    CONSERVADOR(0.20, 0.10),
    MODERADAMENTE_CONSERVADOR(0.30, 0.12),
    MODERADO(0.40, 0.14),
    MODERADAMENTE_AGRESIVO(0.50, 0.16),
    AGRESIVO(0.60, 0.18);

    private final double riesgoMax;   // en fracción (20% -> 0.20)
    private final double retornoMin;  // en fracción

    PerfilCliente(double riesgoMax, double retornoMin) {
        this.riesgoMax = riesgoMax;
        this.retornoMin = retornoMin;
    }
    public double riesgoMax() { return riesgoMax; }
    public double retornoMin() { return retornoMin; }
}
