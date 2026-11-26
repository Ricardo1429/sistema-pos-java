package com.restobar.pos.controller;

import com.restobar.pos.model.DetalleOrden;
import com.restobar.pos.model.EstadoItem;
import com.restobar.pos.model.Orden;
import com.restobar.pos.repository.DetalleOrdenRepository;
import com.restobar.pos.repository.OrdenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/cocina")
public class CocinaController {

    private final DetalleOrdenRepository detalleOrdenRepository;
    private final OrdenRepository ordenRepository;

    public CocinaController(DetalleOrdenRepository detalleOrdenRepository,
                            OrdenRepository ordenRepository) {
        this.detalleOrdenRepository = detalleOrdenRepository;
        this.ordenRepository = ordenRepository;
    }

    @GetMapping("")
    public String kds(Model model) {
        model.addAttribute("pendientes", porEstado(EstadoItem.PENDIENTE));
        model.addAttribute("enCocina", porEstado(EstadoItem.EN_COCINA));
        model.addAttribute("listos", porEstado(EstadoItem.LISTO));
        return "cocina/kds";
    }

    @PostMapping("/detalles/{id}/iniciar")
    @Transactional
    public String iniciar(@PathVariable Long id) {
        cambiarEstado(id, EstadoItem.EN_COCINA);
        return "redirect:/cocina";
    }

    @PostMapping("/detalles/{id}/listo")
    @Transactional
    public String listo(@PathVariable Long id) {
        cambiarEstado(id, EstadoItem.LISTO);
        return "redirect:/cocina";
    }

    @PostMapping("/detalles/{id}/entregado")
    @Transactional
    public String entregado(@PathVariable Long id) {
        cambiarEstado(id, EstadoItem.ENTREGADO);
        return "redirect:/cocina";
    }

    @PostMapping("/ordenes/{id}/lista")
    @Transactional
    public String marcarOrdenLista(@PathVariable Long id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        orden.marcarLista();
        ordenRepository.save(orden);
        return "redirect:/cocina";
    }

    private List<DetalleOrden> porEstado(EstadoItem estado) {
        return detalleOrdenRepository.findByEstadoItem(estado).stream()
                .sorted(Comparator.comparing(DetalleOrden::getId))
                .toList();
    }

    private void cambiarEstado(Long detalleId, EstadoItem estado) {
        DetalleOrden detalle = detalleOrdenRepository.findById(detalleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        detalle.setEstadoItem(estado);
        detalleOrdenRepository.save(detalle);
    }
}
