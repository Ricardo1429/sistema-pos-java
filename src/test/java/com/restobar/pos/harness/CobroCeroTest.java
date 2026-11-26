package com.restobar.pos.harness;

import com.restobar.pos.model.CanalOrden;
import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.EstadoTurno;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.Producto;
import com.restobar.pos.model.TicketVenta;
import com.restobar.pos.model.TurnoCaja;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.ProductoRepository;
import com.restobar.pos.repository.TicketVentaRepository;
import com.restobar.pos.repository.TurnoCajaRepository;
import com.restobar.pos.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de cobro con monto cero: se rechaza de forma controlada cuando la orden
 * tiene total positivo y se permite cuando el total es cero.
 *
 * <p>Cada caso crea mesa, orden y producto con identificadores únicos para no colisionar
 * con el seed ni con los demás tests del harness que comparten el contexto H2.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"ADMIN"})
class CobroCeroTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final String MENSAJE_MONTO =
            "El monto recibido debe ser mayor que cero para esta orden";
    private static final Pattern REDIRECT_TICKET = Pattern.compile("^/caja/ticket/(\\d+)$");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private TicketVentaRepository ticketVentaRepository;

    @Autowired
    private TurnoCajaRepository turnoCajaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void cobrar_montoCeroConTotalPositivo_devuelve400ControladoYNoModificaOrden() throws Exception {
        asegurarTurnoAbierto();
        Mesa mesa = guardarMesaOcupada();
        Orden orden = guardarOrdenLista(mesa, true);
        assertThat(orden.getTotal()).isGreaterThan(BigDecimal.ZERO);
        long ticketsAntes = ticketVentaRepository.count();

        String cuerpo = mockMvc.perform(post("/caja/cobrar")
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("ordenId", orden.getId().toString())
                        .param("metodoPago", "EFECTIVO")
                        .param("montoRecibido", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(MENSAJE_MONTO))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo)
                .as("La respuesta debe ser el JSON controlado, sin 500 ni Whitelabel")
                .doesNotContain("Whitelabel Error Page", "Ha ocurrido un error inesperado", "Exception");
        assertThat(ordenRepository.findById(orden.getId()).orElseThrow().getEstado())
                .as("Un cobro rechazado no debe pagar la orden")
                .isEqualTo(EstadoOrden.LISTA);
        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .as("Un cobro rechazado no debe liberar la mesa")
                .isEqualTo(EstadoMesa.OCUPADA);
        assertThat(ticketVentaRepository.findByOrdenId(orden.getId())).isEmpty();
        assertThat(ticketVentaRepository.count()).as("No se debe crear ningún ticket").isEqualTo(ticketsAntes);
    }

    @Test
    void cobrar_montoCeroConTotalCero_siCobraYGuardaTicket() throws Exception {
        asegurarTurnoAbierto();
        Mesa mesa = guardarMesaOcupada();
        Orden orden = guardarOrdenLista(mesa, false);
        assertThat(orden.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        long ticketsAntes = ticketVentaRepository.count();

        String redirect = mockMvc.perform(post("/caja/cobrar")
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("ordenId", orden.getId().toString())
                        .param("metodoPago", "EFECTIVO")
                        .param("montoRecibido", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        Matcher matcher = REDIRECT_TICKET.matcher(redirect == null ? "" : redirect);
        assertThat(matcher.matches()).as("Redirect inesperado: %s", redirect).isTrue();
        Long ticketId = Long.valueOf(matcher.group(1));

        TicketVenta ticket = ticketVentaRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getOrden().getId()).isEqualTo(orden.getId());
        assertThat(ticket.getMontoRecibido()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ticket.getCambioDevuelto()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ordenRepository.findById(orden.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoOrden.PAGADA);
        assertThat(ticketVentaRepository.count()).isEqualTo(ticketsAntes + 1);
    }

    @Test
    void cobrar_montoNegativo_devuelve400Controlado() throws Exception {
        asegurarTurnoAbierto();
        Mesa mesa = guardarMesaOcupada();
        Orden orden = guardarOrdenLista(mesa, true);
        long ticketsAntes = ticketVentaRepository.count();

        String cuerpo = mockMvc.perform(post("/caja/cobrar")
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("ordenId", orden.getId().toString())
                        .param("metodoPago", "EFECTIVO")
                        .param("montoRecibido", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(MENSAJE_MONTO))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo)
                .as("La respuesta debe ser el JSON controlado, sin 500 ni Whitelabel")
                .doesNotContain("Whitelabel Error Page", "Ha ocurrido un error inesperado", "Exception");
        assertThat(ordenRepository.findById(orden.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoOrden.LISTA);
        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.OCUPADA);
        assertThat(ticketVentaRepository.findByOrdenId(orden.getId())).isEmpty();
        assertThat(ticketVentaRepository.count()).isEqualTo(ticketsAntes);
    }

    private TurnoCaja asegurarTurnoAbierto() {
        return turnoCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc(EstadoTurno.ABIERTO)
                .orElseGet(() -> {
                    TurnoCaja turno = new TurnoCaja();
                    turno.setCajero(usuarioRepository.findByUsername("admin").orElseThrow());
                    turno.setFechaApertura(LocalDateTime.now());
                    turno.setBaseInicial(BigDecimal.ZERO);
                    turno.setTotalVentasEfectivo(BigDecimal.ZERO);
                    turno.setTotalVentasDigital(BigDecimal.ZERO);
                    turno.setEstado(EstadoTurno.ABIERTO);
                    return turnoCajaRepository.save(turno);
                });
    }

    private Mesa guardarMesaOcupada() {
        Mesa mesa = new Mesa();
        mesa.setNumero("CC-" + SECUENCIA.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 6));
        mesa.setZona(ZonaMesa.SALON);
        mesa.setCapacidad(4);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        mesa.abrirOrden();
        return mesaRepository.save(mesa);
    }

    private Orden guardarOrdenLista(Mesa mesa, boolean conProducto) {
        Usuario mesero = usuarioRepository.findByUsername("admin").orElseThrow();
        Orden orden = new Orden();
        orden.setCanal(CanalOrden.MESA);
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setEstado(EstadoOrden.ABIERTA);
        if (conProducto) {
            orden.agregarProducto(guardarProducto(), 1);
        }
        orden.recalcularTotales();
        orden.enviarACocina();
        orden.marcarLista();
        return ordenRepository.save(orden);
    }

    private Producto guardarProducto() {
        String sufijo = SECUENCIA.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6);
        Producto producto = new Producto();
        producto.setCodigo("CCP-" + sufijo);
        producto.setNombre("Producto cobro cero " + sufijo);
        producto.setCategoria(CategoriaProducto.PLATO_FUERTE);
        producto.setPrecio(new BigDecimal("12.50"));
        producto.setStockActual(20);
        producto.setStockMinimo(0);
        producto.setDisponible(true);
        return productoRepository.save(producto);
    }
}
