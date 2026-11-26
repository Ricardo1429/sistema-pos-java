package com.restobar.pos.repository;

import com.restobar.pos.model.Orden;
import com.restobar.pos.model.EstadoOrden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {

    List<Orden> findByEstado(EstadoOrden estado);

    List<Orden> findByEstadoIn(java.util.Collection<EstadoOrden> estados);

    List<Orden> findByMesaId(Long mesaId);

    List<Orden> findAllByOrderByCreatedAtDesc();

    boolean existsByNumeroOrden(String numeroOrden);

    long countByEstado(EstadoOrden estado);
}