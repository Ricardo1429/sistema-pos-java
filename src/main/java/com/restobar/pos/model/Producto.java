package com.restobar.pos.model;

import com.restobar.pos.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaProducto categoria;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Min(0)
    @Column(nullable = false)
    private int stockActual;

    @Min(0)
    @Column(nullable = false)
    private int stockMinimo;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(length = 255)
    private String imagenUrl;

    @Version
    private Long version;

    public Producto() {
    }

    public void validarDisponibilidad(int cantidad) {
        if (!disponible) {
            throw new ReglaNegocioException("El producto " + nombre + " no está disponible");
        }
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser mayor a cero");
        }
        if (stockActual < cantidad) {
            throw new ReglaNegocioException(
                    "Stock insuficiente de " + nombre + ": disponible " + stockActual + ", solicitado " + cantidad);
        }
    }

    public void descontarStock(int cantidad) {
        validarDisponibilidad(cantidad);
        this.stockActual -= cantidad;
        if (this.stockActual == 0) {
            this.disponible = false;
        }
    }

    public void reponerStock(int cantidad) {
        this.stockActual += cantidad;
        this.disponible = true;
    }

    public boolean stockBajo() {
        return stockActual <= stockMinimo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public CategoriaProducto getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaProducto categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public int getStockActual() {
        return stockActual;
    }

    public void setStockActual(int stockActual) {
        this.stockActual = stockActual;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(int stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
