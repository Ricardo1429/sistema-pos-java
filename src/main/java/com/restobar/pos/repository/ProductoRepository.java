package com.restobar.pos.repository;

import com.restobar.pos.model.Producto;
import com.restobar.pos.model.CategoriaProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByCategoria(CategoriaProducto categoria);

    List<Producto> findByDisponibleTrue();

    Optional<Producto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Producto> findAllByOrderByNombreAsc();
}