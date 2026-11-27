package com.restobar.pos.model;

import com.restobar.pos.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Entity
@Table(name = "orden")
public class Orden {

    public static final BigDecimal TASA_IMPOCONSUMO = new BigDecimal("0.08");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 40)
    private String numeroOrden;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalOrden canal;

    @Column(length = 60)
    private String codigoExternoDelivery;

    @ManyToOne
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    @ManyToOne
    @JoinColumn(name = "mesero_id")
    private Usuario mesero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrden estado = EstadoOrden.ABIERTA;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal impoconsumo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal propina = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleOrden> detalles = new ArrayList<>();

    public Orden() {
    }

    public void agregarProducto(Producto producto, int cantidad) {
        if (estado != EstadoOrden.ABIERTA) {
            throw new ReglaNegocioException(
                    "La orden " + numeroOrden + " no permite agregar productos (estado: " + estado + ")");
        }
        validarCanal();
        producto.descontarStock(cantidad);
        DetalleOrden existente = buscarDetallePendiente(producto);
        if (existente != null) {
            existente.setCantidad(existente.getCantidad() + cantidad);
        } else {
            DetalleOrden detalle = new DetalleOrden();
            detalle.setOrden(this);
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(producto.getPrecio());
            this.detalles.add(detalle);
        }
        recalcularTotales();
    }

    public void anularDetalle(Long detalleId, Usuario actual) {
        DetalleOrden detalle = this.detalles.stream()
                .filter(d -> d.getId() != null && d.getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Detalle no encontrado"));
        if (detalle.getEstadoItem() != EstadoItem.PENDIENTE) {
            if (actual == null || !actual.esSupervisor()) {
                throw new ReglaNegocioException(
                        "Solo un SUPERVISOR puede anular un ítem ya enviado a cocina");
            }
            detalle.setAutorizadoAnulacionPor(actual);
        }
        detalle.getProducto().reponerStock(detalle.getCantidad());
        detalle.setEstadoItem(EstadoItem.ANULADO);
        recalcularTotales();
    }

    public void recalcularTotales() {
        BigDecimal suma = BigDecimal.ZERO;
        for (DetalleOrden detalle : detalles) {
            if (detalle.getEstadoItem() != EstadoItem.ANULADO) {
                suma = suma.add(detalle.subtotal());
            }
        }
        this.subtotal = suma;
        this.impoconsumo = suma.multiply(TASA_IMPOCONSUMO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal propinaSegura = propina == null ? BigDecimal.ZERO : propina;
        this.total = this.subtotal.add(this.impoconsumo).add(propinaSegura);
    }

    public void validarCanal() {
        if (esDelivery()) {
            if (codigoExternoDelivery == null || codigoExternoDelivery.isBlank()) {
                throw new ReglaNegocioException(
                        "El canal " + canal + " requiere código externo de delivery");
            }
            if (mesa != null) {
                throw new ReglaNegocioException(
                        "El canal " + canal + " no puede estar asociado a una mesa");
            }
        }
        if (canal == CanalOrden.MESA && mesa == null) {
            throw new ReglaNegocioException("El canal MESA requiere una mesa asignada");
        }
    }

    public void enviarACocina() {
        if (estado != EstadoOrden.ABIERTA) {
            throw new ReglaNegocioException(
                    "La orden " + numeroOrden + " no puede enviarse a cocina (estado: " + estado + ")");
        }
        this.estado = EstadoOrden.EN_PREPARACION;
        for (DetalleOrden detalle : detalles) {
            if (detalle.getEstadoItem() == EstadoItem.PENDIENTE) {
                detalle.setEstadoItem(EstadoItem.EN_COCINA);
            }
        }
    }

    public void marcarLista() {
        if (estado != EstadoOrden.EN_PREPARACION) {
            throw new ReglaNegocioException(
                    "La orden " + numeroOrden + " no puede marcarse lista (estado: " + estado + ")");
        }
        this.estado = EstadoOrden.LISTA;
        for (DetalleOrden detalle : detalles) {
            if (detalle.getEstadoItem() == EstadoItem.EN_COCINA) {
                detalle.setEstadoItem(EstadoItem.LISTO);
            }
        }
    }

    public void marcarListaSiTodoEntregado() {
        if (estado != EstadoOrden.EN_PREPARACION) {
            return;
        }
        boolean todoEntregado = detalles.stream()
                .filter(detalle -> detalle.getEstadoItem() != EstadoItem.ANULADO)
                .allMatch(detalle -> detalle.getEstadoItem() == EstadoItem.ENTREGADO);
        if (todoEntregado) {
            this.estado = EstadoOrden.LISTA;
        }
    }

    public void procesarPagoPOS() {
        if (estado == EstadoOrden.PAGADA) {
            throw new ReglaNegocioException("La orden ya fue pagada");
        }
        if (estado != EstadoOrden.LISTA && estado != EstadoOrden.EN_PREPARACION) {
            throw new ReglaNegocioException(
                    "La orden " + numeroOrden + " no puede pagarse (estado: " + estado + ")");
        }
        this.estado = EstadoOrden.PAGADA;
        if (mesa != null) {
            mesa.liberar();
        }
    }

    public boolean esDelivery() {
        return canal == CanalOrden.RAPPI
                || canal == CanalOrden.UBER_EATS
                || canal == CanalOrden.DIDI_FOOD;
    }

    @PrePersist
    private void prePersist() {
        if (numeroOrden == null) {
            numeroOrden = "ORD-" + System.currentTimeMillis()
                    + "-" + ThreadLocalRandom.current().nextInt(100, 1000);
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    private DetalleOrden buscarDetallePendiente(Producto producto) {
        for (DetalleOrden detalle : detalles) {
            if (detalle.getEstadoItem() != EstadoItem.PENDIENTE) {
                continue;
            }
            Producto otro = detalle.getProducto();
            if (otro == producto) {
                return detalle;
            }
            if (otro != null && otro.getId() != null && otro.getId().equals(producto.getId())) {
                return detalle;
            }
        }
        return null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public void setNumeroOrden(String numeroOrden) {
        this.numeroOrden = numeroOrden;
    }

    public CanalOrden getCanal() {
        return canal;
    }

    public void setCanal(CanalOrden canal) {
        this.canal = canal;
    }

    public String getCodigoExternoDelivery() {
        return codigoExternoDelivery;
    }

    public void setCodigoExternoDelivery(String codigoExternoDelivery) {
        this.codigoExternoDelivery = codigoExternoDelivery;
    }

    public Mesa getMesa() {
        return mesa;
    }

    public void setMesa(Mesa mesa) {
        this.mesa = mesa;
    }

    public Usuario getMesero() {
        return mesero;
    }

    public void setMesero(Usuario mesero) {
        this.mesero = mesero;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrden estado) {
        this.estado = estado;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getImpoconsumo() {
        return impoconsumo;
    }

    public void setImpoconsumo(BigDecimal impoconsumo) {
        this.impoconsumo = impoconsumo;
    }

    public BigDecimal getPropina() {
        return propina;
    }

    public void setPropina(BigDecimal propina) {
        this.propina = propina;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<DetalleOrden> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetalleOrden> detalles) {
        this.detalles = detalles;
    }
}
