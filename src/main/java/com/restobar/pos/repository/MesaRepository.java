package com.restobar.pos.repository;

import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.ZonaMesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    List<Mesa> findByEstado(EstadoMesa estado);

    List<Mesa> findByZona(ZonaMesa zona);

    Optional<Mesa> findByNumero(String numero);

    boolean existsByNumero(String numero);

    List<Mesa> findAllByOrderByNumeroAsc();
}