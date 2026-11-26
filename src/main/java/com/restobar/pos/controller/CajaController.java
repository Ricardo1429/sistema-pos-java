package com.restobar.pos.controller;

import com.restobar.pos.config.UsuarioActualResolver;
import com.restobar.pos.exception.ReglaNegocioException;
import com.restobar.pos.model.DetalleOrden;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.EstadoTurno;
import com.restobar.pos.model.MetodoPago;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.TicketVenta;
import com.restobar.pos.model.TurnoCaja;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.TicketVentaRepository;
import com.restobar.pos.repository.TurnoCajaRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/caja")
public class CajaController {

    private final TurnoCajaRepository turnoCajaRepository;
    private final MesaRepository mesaRepository;
    private final OrdenRepository ordenRepository;
    private final TicketVentaRepository ticketVentaRepository;
    private final UsuarioActualResolver usuarioActualResolver;

    public CajaController(TurnoCajaRepository turnoCajaRepository,
                          MesaRepository mesaRepository,
                          OrdenRepository ordenRepository,
                          TicketVentaRepository ticketVentaRepository,
                          UsuarioActualResolver usuarioActualResolver) {
        this.turnoCajaRepository = turnoCajaRepository;
        this.mesaRepository = mesaRepository;
        this.ordenRepository = ordenRepository;
        this.ticketVentaRepository = ticketVentaRepository;
        this.usuarioActualResolver = usuarioActualResolver;
    }

    @GetMapping("")
    @Transactional(readOnly = true)
    public String facturacion(@RequestParam(required = false) Long ordenId, Model model) {
        TurnoCaja turnoAbierto = turnoCajaRepository
                .findFirstByEstadoOrderByFechaAperturaDesc(EstadoTurno.ABIERTO)
                .orElse(null);

        List<TicketVenta> ticketsHoy = ticketVentaRepository.findAll().stream()
                .filter(ticket -> ticket.getFechaEmision() != null
                        && ticket.getFechaEmision().toLocalDate().equals(LocalDate.now()))
                .toList();

        model.addAttribute("turnoAbierto", turnoAbierto);
        model.addAttribute("mesasPorCobrar", mesaRepository.findByEstado(EstadoMesa.POR_COBRAR));
        model.addAttribute("ordenesListas", ordenRepository.findByEstado(EstadoOrden.LISTA));
        model.addAttribute("ticketsHoy", ticketsHoy);

        Orden ordenSeleccionada = null;
        List<DetalleOrden> detalles = null;
        if (ordenId != null) {
            ordenSeleccionada = ordenRepository.findById(ordenId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            detalles = new ArrayList<>(ordenSeleccionada.getDetalles());
        }
        model.addAttribute("ordenSeleccionada", ordenSeleccionada);
        model.addAttribute("detalles", detalles);
        return "caja/facturacion";
    }

    @PostMapping("/turno/abrir")
    @Transactional
    public String abrirTurno(@RequestParam BigDecimal baseInicial,
                             Authentication authentication) {
        if (turnoCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc(EstadoTurno.ABIERTO).isPresent()) {
            throw new ReglaNegocioException("Ya existe un turno de caja abierto");
        }
        if (baseInicial == null || baseInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReglaNegocioException("La base inicial no puede ser negativa");
        }
        Usuario usuarioActual = usuarioActualResolver.resolver(authentication);
        TurnoCaja turno = new TurnoCaja();
        turno.setCajero(usuarioActual);
        turno.setFechaApertura(LocalDateTime.now());
        turno.setBaseInicial(baseInicial);
        turno.setEstado(EstadoTurno.ABIERTO);
        turnoCajaRepository.save(turno);
        return "redirect:/caja";
    }

    @PostMapping("/turno/cerrar")
    @Transactional
    public String cerrarTurno(@RequestParam BigDecimal efectivoDeclarado,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        TurnoCaja turnoAbierto = turnoCajaRepository
                .findFirstByEstadoOrderByFechaAperturaDesc(EstadoTurno.ABIERTO)
                .orElseThrow(() -> new ReglaNegocioException("Debe abrir un turno de caja"));
        Usuario usuarioActual = usuarioActualResolver.resolver(authentication);
        turnoAbierto.cerrarTurno(efectivoDeclarado, mesaRepository.findAll(), usuarioActual);
        turnoCajaRepository.save(turnoAbierto);
        redirectAttributes.addFlashAttribute("exito", "Turno de caja cerrado");
        return "redirect:/caja";
    }

    @PostMapping("/cobrar")
    @Transactional
    public String cobrar(@RequestParam Long ordenId,
                         @RequestParam MetodoPago metodoPago,
                         @RequestParam BigDecimal montoRecibido,
                         @RequestParam(defaultValue = "0") BigDecimal propina,
                         @RequestParam(required = false) String clienteNombre) {
        TurnoCaja turno = turnoCajaRepository
                .findFirstByEstadoOrderByFechaAperturaDesc(EstadoTurno.ABIERTO)
                .orElseThrow(() -> new ReglaNegocioException("Debe abrir un turno de caja"));

        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (montoRecibido == null || montoRecibido.signum() < 0
                || (montoRecibido.signum() == 0
                    && orden.getTotal() != null && orden.getTotal().signum() > 0)) {
            throw new ReglaNegocioException("El monto recibido debe ser mayor que cero para esta orden");
        }

        if (propina != null && propina.compareTo(BigDecimal.ZERO) > 0) {
            orden.setPropina(propina);
            orden.recalcularTotales();
        }

        orden.procesarPagoPOS();

        TicketVenta ticket = new TicketVenta();
        ticket.setConsecutivo("T-" + System.currentTimeMillis());
        ticket.setOrden(orden);
        ticket.setTurnoCaja(turno);
        ticket.setFechaEmision(LocalDateTime.now());
        ticket.setMetodoPago(metodoPago);
        ticket.setMontoRecibido(montoRecibido);
        ticket.setClienteNombre(clienteNombre == null || clienteNombre.isBlank()
                ? "Consumidor Final" : clienteNombre);
        ticket.calcularCambio();

        turno.registrarVenta(metodoPago, orden.getTotal());

        ordenRepository.save(orden);
        turnoCajaRepository.save(turno);
        ticketVentaRepository.save(ticket);

        return "redirect:/caja/ticket/" + ticket.getId();
    }

    @GetMapping("/ticket/{id}")
    @Transactional(readOnly = true)
    public String ticket(@PathVariable Long id, Model model) {
        TicketVenta ticket = ticketVentaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("ticket", ticket);
        model.addAttribute("detalles", ticket.getOrden() != null
                ? new ArrayList<>(ticket.getOrden().getDetalles())
                : new ArrayList<>());
        return "caja/ticket";
    }
}
