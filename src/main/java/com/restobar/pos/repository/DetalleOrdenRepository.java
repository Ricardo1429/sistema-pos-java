package com.restobar.pos.repository;

import com.restobar.pos.model.DetalleOrden;
import com.restobar.pos.model.EstadoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DetalleOrdenRepository extends JpaRepository<DetalleOrden, Long> {

    List<DetalleOrden> findByEstadoItem(EstadoItem estadoItem);

    List<DetalleOrden> findByOrdenId(Long ordenId);
}