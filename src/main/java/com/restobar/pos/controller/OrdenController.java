package com.restobar.pos.controller;

import com.restobar.pos.config.UsuarioActualResolver;
import com.restobar.pos.exception.ReglaNegocioException;
import com.restobar.pos.model.CanalOrden;
import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.DetalleOrden;
import com.restobar.pos.model.EstadoItem;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.Producto;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/ordenes")
public class OrdenController {

    private static final int LIMITE_LISTADO = 50;

    private final OrdenRepository ordenRepository;
    private final ProductoRepository productoRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioActualResolver usuarioActualResolver;

    public OrdenController(OrdenRepository ordenRepository,
                           ProductoRepository productoRepository,
                           MesaRepository mesaRepository,
                           UsuarioActualResolver usuarioActualResolver) {
        this.ordenRepository = ordenRepository;
        this.productoRepository = productoRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioActualResolver = usuarioActualResolver;
    }

    @GetMapping("")
    public String listar(Model model) {
        List<Orden> ordenes = ordenRepository.findAllByOrderByCreatedAtDesc().stream()
                .limit(LIMITE_LISTADO)
                .toList();
        model.addAttribute("ordenes", ordenes);
        return "ordenes/listar";
    }

    @GetMapping("/{id}/pos")
    @Transactional(readOnly = true)
    public String pos(@PathVariable Long id, Model model) {
        Orden orden = buscarOrden(id);
        List<Producto> productos = productoRepository.findByDisponibleTrue().stream()
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .toList();

        model.addAttribute("orden", orden);
        model.addAttribute("detalles", new ArrayList<>(orden.getDetalles()));
        model.addAttribute("productos", productos);
        model.addAttribute("categorias", CategoriaProducto.values());
        return "ordenes/pos";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("canales", CanalOrden.values());
        model.addAttribute("mesasDisponibles", mesaRepository.findAllByOrderByNumeroAsc().stream()
                .filter(m -> m.estaDisponible()).toList());
        return "ordenes/nueva";
    }

    @PostMapping("/nueva")
    @Transactional
    public String crear(@RequestParam CanalOrden canal,
                        @RequestParam(required = false) String codigoExternoDelivery,
                        @RequestParam(required = false) Long mesaId,
                        Authentication authentication) {
        Usuario usuarioActual = usuarioActualResolver.resolver(authentication);
        Orden orden = new Orden();
        orden.setCanal(canal);
        orden.setCodigoExternoDelivery(codigoExternoDelivery);
        if (canal == CanalOrden.MESA) {
            if (mesaId == null) {
                throw new ReglaNegocioException("Seleccione una mesa para el canal MESA");
            }
            Mesa mesa = mesaRepository.findById(mesaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (mesa.estaDisponible()) {
                mesa.abrirOrden();
            }
            orden.setMesa(mesa);
            mesaRepository.save(mesa);
        }
        orden.setMesero(usuarioActual);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.validarCanal();
        ordenRepository.save(orden);
        return "redirect:/ordenes/" + orden.getId() + "/pos";
    }

    @PostMapping("/{id}/agregar")
    @Transactional
    public String agregar(@PathVariable Long id,
                          @RequestParam Long productoId,
                          @RequestParam(defaultValue = "1") int cantidad,
                          @RequestParam(required = false) String observaciones,
                          RedirectAttributes redirectAttributes) {
        Orden orden = buscarOrden(id);
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        orden.agregarProducto(producto, cantidad);
        if (observaciones != null && !observaciones.isBlank()) {
            DetalleOrden ultimo = null;
            for (DetalleOrden detalle : orden.getDetalles()) {
                if (detalle.getProducto() != null && detalle.getProducto().getId() != null
                        && detalle.getProducto().getId().equals(productoId)) {
                    ultimo = detalle;
                }
            }
            if (ultimo != null) {
                ultimo.setObservaciones(observaciones);
            }
        }
        ordenRepository.save(orden);
        redirectAttributes.addFlashAttribute("exito", "Producto agregado a la orden");
        return "redirect:/ordenes/" + id + "/pos";
    }

    @PostMapping("/{id}/detalles/{detalleId}/anular")
    @Transactional
    public String anularDetalle(@PathVariable Long id,
                                @PathVariable Long detalleId,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        Usuario usuarioActual = usuarioActualResolver.resolver(authentication);
        Orden orden = buscarOrden(id);
        orden.anularDetalle(detalleId, usuarioActual);
        ordenRepository.save(orden);
        redirectAttributes.addFlashAttribute("exito", "Ítem anulado");
        return "redirect:/ordenes/" + id + "/pos";
    }

    @PostMapping("/{id}/enviar-cocina")
    @Transactional
    public String enviarACocina(@PathVariable Long id) {
        Orden orden = buscarOrden(id);
        orden.enviarACocina();
        ordenRepository.save(orden);
        return "redirect:/cocina";
    }

    @PostMapping("/{id}/anular")
    @Transactional
    public String anular(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioActual = usuarioActualResolver.resolver(authentication);
        if (usuarioActual == null || !usuarioActual.esSupervisor()) {
            throw new ReglaNegocioException("Solo un SUPERVISOR puede anular la orden");
        }
        Orden orden = buscarOrden(id);
        if (orden.getEstado() != EstadoOrden.ANULADA) {
            for (DetalleOrden detalle : orden.getDetalles()) {
                if (detalle.getEstadoItem() != EstadoItem.ANULADO) {
                    detalle.getProducto().reponerStock(detalle.getCantidad());
                }
            }
        }
        orden.setEstado(EstadoOrden.ANULADA);
        if (orden.getMesa() != null) {
            orden.getMesa().liberar();
        }
        ordenRepository.save(orden);
        return "redirect:/ordenes";
    }

    private Orden buscarOrden(Long id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
