package com.restobar.pos.model;

import com.restobar.pos.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "turno_caja")
public class TurnoCaja {

    private static final BigDecimal TOLERANCIA_DESCUADRE = new BigDecimal("0.01");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "cajero_id", nullable = false)
    private Usuario cajero;

    @Column(nullable = false)
    private LocalDateTime fechaApertura;

    private LocalDateTime fechaCierre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseInicial = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalVentasEfectivo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalVentasDigital = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal efectivoCierreDeclarado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTurno estado = EstadoTurno.ABIERTO;

    @Version
    private Long version;

    public TurnoCaja() {
    }

    public void registrarVenta(MetodoPago metodo, BigDecimal monto) {
        if (estado != EstadoTurno.ABIERTO) {
            throw new ReglaNegocioException("El turno de caja no está ABIERTO (estado: " + estado + ")");
        }
        if (metodo == MetodoPago.EFECTIVO) {
            this.totalVentasEfectivo = this.totalVentasEfectivo.add(monto);
        } else {
            this.totalVentasDigital = this.totalVentasDigital.add(monto);
        }
    }

    public void cerrarTurno(BigDecimal efectivoDeclarado, List<Mesa> mesasActivas, Usuario actual) {
        if (estado != EstadoTurno.ABIERTO) {
            throw new ReglaNegocioException("El turno de caja ya está cerrado");
        }
        if (mesasActivas != null && !mesasActivas.isEmpty()) {
            for (Mesa mesa : mesasActivas) {
                if (mesa.getEstado() == EstadoMesa.OCUPADA || mesa.getEstado() == EstadoMesa.POR_COBRAR) {
                    throw new ReglaNegocioException(
                            "La mesa " + mesa.getNumero() + " sigue activa (estado: " + mesa.getEstado()
                                    + "); no se puede cerrar el turno");
                }
            }
        }
        BigDecimal baseSegura = baseInicial == null ? BigDecimal.ZERO : baseInicial;
        BigDecimal ventasEfectivoSeguras = totalVentasEfectivo == null ? BigDecimal.ZERO : totalVentasEfectivo;
        BigDecimal esperado = baseSegura.add(ventasEfectivoSeguras);
        BigDecimal declarado = efectivoDeclarado == null ? BigDecimal.ZERO : efectivoDeclarado;
        BigDecimal diferencia = declarado.subtract(esperado);
        if (diferencia.abs().compareTo(TOLERANCIA_DESCUADRE) > 0
                && (actual == null || !actual.esSupervisor())) {
            throw new ReglaNegocioException("Descuadre de caja de "
                    + diferencia.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + " requiere autorización de SUPERVISOR");
        }
        this.efectivoCierreDeclarado = declarado;
        this.fechaCierre = LocalDateTime.now();
        this.estado = EstadoTurno.CERRADO;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getCajero() {
        return cajero;
    }

    public void setCajero(Usuario cajero) {
        this.cajero = cajero;
    }

    public LocalDateTime getFechaApertura() {
        return fechaApertura;
    }

    public void setFechaApertura(LocalDateTime fechaApertura) {
        this.fechaApertura = fechaApertura;
    }

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public BigDecimal getBaseInicial() {
        return baseInicial;
    }

    public void setBaseInicial(BigDecimal baseInicial) {
        this.baseInicial = baseInicial;
    }

    public BigDecimal getTotalVentasEfectivo() {
        return totalVentasEfectivo;
    }

    public void setTotalVentasEfectivo(BigDecimal totalVentasEfectivo) {
        this.totalVentasEfectivo = totalVentasEfectivo;
    }

    public BigDecimal getTotalVentasDigital() {
        return totalVentasDigital;
    }

    public void setTotalVentasDigital(BigDecimal totalVentasDigital) {
        this.totalVentasDigital = totalVentasDigital;
    }

    public BigDecimal getEfectivoCierreDeclarado() {
        return efectivoCierreDeclarado;
    }

    public void setEfectivoCierreDeclarado(BigDecimal efectivoCierreDeclarado) {
        this.efectivoCierreDeclarado = efectivoCierreDeclarado;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
