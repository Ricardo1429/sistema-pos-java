package com.restobar.pos.controller;

import com.restobar.pos.config.UsuarioActualResolver;
import com.restobar.pos.exception.ReglaNegocioException;
import com.restobar.pos.model.CanalOrden;
import com.restobar.pos.model.EstadoItem;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mesas")
public class MesaController {

    private final MesaRepository mesaRepository;
    private final OrdenRepository ordenRepository;
    private final UsuarioActualResolver usuarioActualResolver;

    public MesaController(MesaRepository mesaRepository,
                          OrdenRepository ordenRepository,
                          UsuarioActualResolver usuarioActualResolver) {
        this.mesaRepository = mesaRepository;
        this.ordenRepository = ordenRepository;
        this.usuarioActualResolver = usuarioActualResolver;
    }

    @GetMapping("")
    public String listar(Model model) {
        List<Mesa> mesas = mesaRepository.findAllByOrderByNumeroAsc();
        Map<String, Long> conteo = new LinkedHashMap<>();
        conteo.put("disponibles", mesas.stream()
                .filter(mesa -> mesa.getEstado() == EstadoMesa.DISPONIBLE).count());
        conteo.put("ocupadas", mesas.stream()
                .filter(mesa -> mesa.getEstado() == EstadoMesa.OCUPADA).count());
        conteo.put("porCobrar", mesas.stream()
                .filter(mesa -> mesa.getEstado() == EstadoMesa.POR_COBRAR).count());

        Set<Long> mesasSinConsumo = mesas.stream()
                .filter(mesa -> mesa.getEstado() == EstadoMesa.OCUPADA)
                .filter(mesa -> {
                    List<Orden> abiertas = ordenRepository.findByMesaId(mesa.getId()).stream()
                            .filter(orden -> orden.getEstado() == EstadoOrden.ABIERTA)
                            .toList();
                    return abiertas.size() == 1 && abiertas.get(0).getDetalles().stream()
                            .noneMatch(detalle -> detalle.getEstadoItem() != EstadoItem.ANULADO);
                })
                .map(Mesa::getId)
                .collect(Collectors.toSet());

        model.addAttribute("mesas", mesas);
        model.addAttribute("zonas", ZonaMesa.values());
        model.addAttribute("conteo", conteo);
        model.addAttribute("mesasSinConsumo", mesasSinConsumo);
        return "mesas/listar";
    }

    @PostMapping("/nueva")
    @Transactional
    public String crear(@RequestParam String numero,
                        @RequestParam ZonaMesa zona,
                        @RequestParam int capacidad,
                        RedirectAttributes redirectAttributes) {
        if (numero == null || numero.isBlank()) {
            throw new ReglaNegocioException("El número de mesa es obligatorio");
        }
        String numeroLimpio = numero.trim();
        if (mesaRepository.existsByNumero(numeroLimpio)) {
            throw new ReglaNegocioException("Ya existe una mesa con el número " + numeroLimpio);
        }
        Mesa mesa = new Mesa();
        mesa.setNumero(numeroLimpio);
        mesa.setZona(zona);
        mesa.setCapacidad(capacidad);
        mesaRepository.save(mesa);
        redirectAttributes.addFlashAttribute("exito", "Mesa " + numeroLimpio + " creada");
        return "redirect:/mesas";
    }

    @PostMapping("/{id}/abrir")
    @Transactional
    public String abrir(@PathVariable Long id, Authentication authentication) {
        Mesa mesa = buscarMesa(id);
        mesa.abrirOrden();

        Usuario usuarioActual = usuarioActualResolver.resolver(authentication);
        Orden orden = new Orden();
        orden.setCanal(CanalOrden.MESA);
        orden.setMesa(mesa);
        orden.setMesero(usuarioActual);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.validarCanal();
        ordenRepository.save(orden);
        return "redirect:/ordenes/" + orden.getId() + "/pos";
    }

    @PostMapping("/{id}/pedir-mas")
    @Transactional
    public String pedirMas(@PathVariable Long id, Authentication authentication) {
        Mesa mesa = buscarMesa(id);
        if (mesa.getEstado() == EstadoMesa.DISPONIBLE) {
            throw new ReglaNegocioException("Abra la mesa antes de pedir");
        }

        Usuario usuarioActual = usuarioActualResolver.resolver(authentication);
        Orden orden = new Orden();
        orden.setCanal(CanalOrden.MESA);
        orden.setMesa(mesa);
        orden.setMesero(usuarioActual);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.validarCanal();
        ordenRepository.save(orden);
        return "redirect:/ordenes/" + orden.getId() + "/pos";
    }

    @PostMapping("/{id}/por-cobrar")
    @Transactional
    public String porCobrar(@PathVariable Long id) {
        Mesa mesa = buscarMesa(id);
        mesa.marcarPorCobrar();
        mesaRepository.save(mesa);
        return "redirect:/caja";
    }

    @PostMapping("/{id}/liberar")
    @Transactional
    public String liberar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Mesa mesa = buscarMesa(id);
        boolean tieneOrdenActiva = ordenRepository.findByMesaId(id).stream()
                .anyMatch(orden -> orden.getEstado() == EstadoOrden.ABIERTA
                        || orden.getEstado() == EstadoOrden.EN_PREPARACION
                        || orden.getEstado() == EstadoOrden.LISTA);
        if (tieneOrdenActiva) {
            throw new ReglaNegocioException("La mesa tiene una orden activa");
        }
        mesa.liberar();
        mesaRepository.save(mesa);
        redirectAttributes.addFlashAttribute("exito", "Mesa " + mesa.getNumero() + " liberada");
        return "redirect:/mesas";
    }

    @PostMapping("/{id}/cerrar-sin-consumo")
    @Transactional
    public String cerrarSinConsumo(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Mesa mesa = buscarMesa(id);
        if (mesa.estaDisponible()) {
            throw new ReglaNegocioException("La mesa ya está disponible");
        }
        Orden orden = ordenRepository.findByMesaId(id).stream()
                .filter(o -> o.getEstado() == EstadoOrden.ABIERTA)
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("La mesa no tiene una orden abierta"));
        if (orden.getDetalles().stream().anyMatch(d -> d.getEstadoItem() != EstadoItem.ANULADO)) {
            throw new ReglaNegocioException("La mesa tiene consumo; use Por cobrar");
        }
        orden.setEstado(EstadoOrden.ANULADA);
        mesa.liberar();
        ordenRepository.save(orden);
        mesaRepository.save(mesa);
        redirectAttributes.addFlashAttribute("exito", "Mesa " + mesa.getNumero() + " cerrada sin consumo");
        return "redirect:/mesas";
    }

    @PostMapping("/{id}/eliminar")
    @Transactional
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Mesa mesa = buscarMesa(id);
        if (!mesa.estaDisponible()) {
            throw new ReglaNegocioException(
                    "Solo se pueden eliminar mesas disponibles (estado: " + mesa.getEstado() + ")");
        }
        mesaRepository.delete(mesa);
        redirectAttributes.addFlashAttribute("exito", "Mesa " + mesa.getNumero() + " eliminada");
        return "redirect:/mesas";
    }

    private Mesa buscarMesa(Long id) {
        return mesaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
