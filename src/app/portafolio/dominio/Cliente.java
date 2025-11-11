package app.portafolio.dominio;

public class Cliente {
    private final double montoTotal;
    private final PerfilCliente perfil;
    private final String[] sectoresPreferidos; // si vacío => sin filtro duro
    private final String[] tiposPreferidos;

    public Cliente(double montoTotal, PerfilCliente perfil, String[] sectores, String[] tipos) {
        this.montoTotal = montoTotal;
        this.perfil = perfil;
        this.sectoresPreferidos = sectores == null ? new String[0] : sectores;
        this.tiposPreferidos = tipos == null ? new String[0] : tipos;
    }

    public double montoTotal() { return montoTotal; }
    public PerfilCliente perfil() { return perfil; }
    public String[] sectoresPreferidos() { return sectoresPreferidos; }
    public String[] tiposPreferidos() { return tiposPreferidos; }
}
