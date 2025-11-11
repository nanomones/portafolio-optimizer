package app.portafolio.dominio;

/** Modelo de un activo del universo de inversión. */
public record Activo(
        String id,
        String ticker,
        String sector,
        String tipo,
        double precioMinimo,        // "InversionMinima": monto mínimo de inversión
        double retornoEsperado,     // "Retorno Esperado"
        double riesgo               // "Riesgo"
) {
    @Override
    public String toString() {
        return id + "{" + ticker + ", " + sector + ", tipo=" + tipo +
               ", min=" + precioMinimo + ", retorno=" + retornoEsperado +
               ", riesgo=" + riesgo + "}";
    }
}
