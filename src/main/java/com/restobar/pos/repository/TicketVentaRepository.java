package com.restobar.pos.repository;

import com.restobar.pos.model.TicketVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketVentaRepository extends JpaRepository<TicketVenta, Long> {

    boolean existsByConsecutivo(String consecutivo);

    Optional<TicketVenta> findByOrdenId(Long ordenId);

    boolean existsByOrdenId(Long ordenId);
}