package com.restobar.pos.controller;

import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.EstadoTurno;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Producto;
import com.restobar.pos.model.TicketVenta;
import com.restobar.pos.model.TurnoCaja;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.ProductoRepository;
import com.restobar.pos.repository.TicketVentaRepository;
import com.restobar.pos.repository.TurnoCajaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Controller
public class DashboardController {

    private final MesaRepository mesaRepository;
    private final OrdenRepository ordenRepository;
    private final ProductoRepository productoRepository;
    private final TicketVentaRepository ticketVentaRepository;
    private final TurnoCajaRepository turnoCajaRepository;

    public DashboardController(MesaRepository mesaRepository,
                               OrdenRepository ordenRepository,
                               ProductoRepository productoRepository,
                               TicketVentaRepository ticketVentaRepository,
                               TurnoCajaRepository turnoCajaRepository) {
        this.mesaRepository = mesaRepository;
        this.ordenRepository = ordenRepository;
        this.productoRepository = productoRepository;
        this.ticketVentaRepository = ticketVentaRepository;
        this.turnoCajaRepository = turnoCajaRepository;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public String index(Model model) {
        List<Mesa> mesas = mesaRepository.findAllByOrderByNumeroAsc();

        List<TicketVenta> ticketsHoy = ticketVentaRepository.findAll().stream()
                .filter(ticket -> ticket.getFechaEmision() != null
                        && ticket.getFechaEmision().toLocalDate().equals(LocalDate.now()))
                .toList();
        BigDecimal ventasHoy = ticketsHoy.stream()
                .map(ticket -> ticket.getOrden() != null ? ticket.getOrden().getTotal() : null)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Producto> alertasStock = productoRepository.findAllByOrderByNombreAsc().stream()
                .filter(Producto::stockBajo)
                .limit(10)
                .toList();

        TurnoCaja turnoAbierto = turnoCajaRepository
                .findFirstByEstadoOrderByFechaAperturaDesc(EstadoTurno.ABIERTO)
                .orElse(null);

        model.addAttribute("mesas", mesas);
        model.addAttribute("zonas", ZonaMesa.values());
        model.addAttribute("totalMesas", (long) mesas.size());
        model.addAttribute("mesasOcupadas", mesas.stream()
                .filter(mesa -> mesa.getEstado() == EstadoMesa.OCUPADA).count());
        model.addAttribute("mesasPorCobrar", mesas.stream()
                .filter(mesa -> mesa.getEstado() == EstadoMesa.POR_COBRAR).count());
        model.addAttribute("ordenesAbiertas", ordenRepository.countByEstado(EstadoOrden.ABIERTA));
        model.addAttribute("ventasHoy", ventasHoy);
        model.addAttribute("ticketsHoy", (long) ticketsHoy.size());
        model.addAttribute("alertasStock", alertasStock);
        model.addAttribute("turnoAbierto", turnoAbierto);
        return "index";
    }
}
