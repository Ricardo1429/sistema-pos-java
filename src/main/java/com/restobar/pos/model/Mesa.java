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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "mesa")
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ZonaMesa zona;

    @Min(1)
    @Column(nullable = false)
    private int capacidad;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMesa estado = EstadoMesa.DISPONIBLE;

    @Version
    private Long version;

    public Mesa() {
    }

    public void abrirOrden() {
        if (estado != EstadoMesa.DISPONIBLE) {
            throw new ReglaNegocioException(
                    "La mesa " + numero + " no está disponible (estado: " + estado + ")");
        }
        this.estado = EstadoMesa.OCUPADA;
    }

    public void marcarPorCobrar() {
        if (estado != EstadoMesa.OCUPADA) {
            throw new ReglaNegocioException(
                    "La mesa " + numero + " no puede pasar a por cobrar (estado: " + estado + ")");
        }
        this.estado = EstadoMesa.POR_COBRAR;
    }

    public void liberar() {
        this.estado = EstadoMesa.DISPONIBLE;
    }

    public boolean estaDisponible() {
        return estado == EstadoMesa.DISPONIBLE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public ZonaMesa getZona() {
        return zona;
    }

    public void setZona(ZonaMesa zona) {
        this.zona = zona;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    public EstadoMesa getEstado() {
        return estado;
    }

    public void setEstado(EstadoMesa estado) {
        this.estado = estado;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
