package com.restobar.pos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_venta")
public class TicketVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String consecutivo;

    @NotNull
    @OneToOne
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @ManyToOne
    @JoinColumn(name = "turno_caja_id")
    private TurnoCaja turnoCaja;

    private LocalDateTime fechaEmision;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodoPago;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoRecibido;

    @Column(precision = 12, scale = 2)
    private BigDecimal cambioDevuelto;

    @Column(length = 120)
    private String clienteNombre = "Consumidor Final";

    public TicketVenta() {
    }

    public void calcularCambio() {
        BigDecimal diferencia = montoRecibido.subtract(orden.getTotal());
        if (diferencia.compareTo(BigDecimal.ZERO) < 0) {
            diferencia = BigDecimal.ZERO;
        }
        this.cambioDevuelto = diferencia.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConsecutivo() {
        return consecutivo;
    }

    public void setConsecutivo(String consecutivo) {
        this.consecutivo = consecutivo;
    }

    public Orden getOrden() {
        return orden;
    }

    public void setOrden(Orden orden) {
        this.orden = orden;
    }

    public TurnoCaja getTurnoCaja() {
        return turnoCaja;
    }

    public void setTurnoCaja(TurnoCaja turnoCaja) {
        this.turnoCaja = turnoCaja;
    }

    public LocalDateTime getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDateTime fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPago metodoPago) {
        this.metodoPago = metodoPago;
    }

    public BigDecimal getMontoRecibido() {
        return montoRecibido;
    }

    public void setMontoRecibido(BigDecimal montoRecibido) {
        this.montoRecibido = montoRecibido;
    }

    public BigDecimal getCambioDevuelto() {
        return cambioDevuelto;
    }

    public void setCambioDevuelto(BigDecimal cambioDevuelto) {
        this.cambioDevuelto = cambioDevuelto;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }
}
