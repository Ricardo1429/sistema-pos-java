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
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Entity
@Table(name = "detalle_orden")
public class DetalleOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "orden_id")
    private Orden orden;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Min(1)
    @Column(nullable = false)
    private int cantidad;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(length = 255)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoItem estadoItem = EstadoItem.PENDIENTE;

    @ManyToOne
    @JoinColumn(name = "autorizado_anulacion_por_id")
    private Usuario autorizadoAnulacionPor;

    public DetalleOrden() {
    }

    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Orden getOrden() {
        return orden;
    }

    public void setOrden(Orden orden) {
        this.orden = orden;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public EstadoItem getEstadoItem() {
        return estadoItem;
    }

    public void setEstadoItem(EstadoItem estadoItem) {
        this.estadoItem = estadoItem;
    }

    public Usuario getAutorizadoAnulacionPor() {
        return autorizadoAnulacionPor;
    }

    public void setAutorizadoAnulacionPor(Usuario autorizadoAnulacionPor) {
        this.autorizadoAnulacionPor = autorizadoAnulacionPor;
    }
}
