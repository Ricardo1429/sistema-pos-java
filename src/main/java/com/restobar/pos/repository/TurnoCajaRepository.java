package com.restobar.pos.repository;

import com.restobar.pos.model.TurnoCaja;
import com.restobar.pos.model.EstadoTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TurnoCajaRepository extends JpaRepository<TurnoCaja, Long> {

    List<TurnoCaja> findByEstado(EstadoTurno estado);

    Optional<TurnoCaja> findFirstByEstadoOrderByFechaAperturaDesc(EstadoTurno estado);
}