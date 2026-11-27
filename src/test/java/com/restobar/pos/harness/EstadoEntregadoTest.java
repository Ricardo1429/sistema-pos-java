package com.restobar.pos.harness;

import com.restobar.pos.model.CanalOrden;
import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.EstadoItem;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.Producto;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.ProductoRepository;
import com.restobar.pos.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de la regla "último ítem entregado deja la orden lista".
 *
 * <p>Cada caso usa datos con sufijo UUID para no colisionar con el seed ni con otros
 * tests del harness que comparten el contexto H2.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"ADMIN"})
class EstadoEntregadoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void marcarListaSiTodoEntregado_conUnDetalleSinEntregar_mantieneEnPreparacion() {
        Orden orden = ordenEnPreparacionConDosDetalles();
        orden.getDetalles().get(0).setEstadoItem(EstadoItem.ENTREGADO);

        orden.marcarListaSiTodoEntregado();

        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.EN_PREPARACION);
    }

    @Test
    void marcarListaSiTodoEntregado_conTodosLosDetallesEntregados_pasaALista() {
        Orden orden = ordenEnPreparacionConDosDetalles();
        orden.getDetalles().forEach(detalle -> detalle.setEstadoItem(EstadoItem.ENTREGADO));

        orden.marcarListaSiTodoEntregado();

        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.LISTA);
    }

    @Test
    void entregado_porHttp_conUnUnicoDetalle_elPosMuestraOrdenListaYDetalleEntregado() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero("EE-" + UUID.randomUUID().toString().substring(0, 8));
        mesa.setZona(ZonaMesa.SALON);
        mesa.setCapacidad(4);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        mesa.abrirOrden();
        mesa = mesaRepository.save(mesa);

        Usuario mesero = usuarioRepository.findByUsername("admin").orElseThrow();
        Orden orden = new Orden();
        orden.setNumeroOrden("EE-" + UUID.randomUUID().toString().substring(0, 8));
        orden.setCanal(CanalOrden.MESA);
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.agregarProducto(productoRepository.save(productoConCodigoUnico()), 1);
        orden.recalcularTotales();
        orden.enviarACocina();
        Orden guardada = ordenRepository.save(orden);
        Long detalleId = guardada.getDetalles().get(0).getId();

        mockMvc.perform(post("/cocina/detalles/{id}/entregado", detalleId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get("/ordenes/{id}/pos", guardada.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("El POS debe mostrar la orden LISTA y el ítem ENTREGADO")
                .contains(">LISTA</span>", ">ENTREGADO</span>")
                .doesNotContain(">EN PREPARACION</span>");
        assertThat(ordenRepository.findById(guardada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoOrden.LISTA);
    }

    private Orden ordenEnPreparacionConDosDetalles() {
        Orden orden = new Orden();
        orden.setNumeroOrden("EE-" + UUID.randomUUID().toString().substring(0, 8));
        orden.setCanal(CanalOrden.MOSTRADOR);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.agregarProducto(productoConCodigoUnico(), 1);
        orden.agregarProducto(productoConCodigoUnico(), 1);
        orden.enviarACocina();
        return orden;
    }

    private Producto productoConCodigoUnico() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Producto producto = new Producto();
        producto.setCodigo("EEP-" + sufijo);
        producto.setNombre("Producto estado entregado " + sufijo);
        producto.setCategoria(CategoriaProducto.PLATO_FUERTE);
        producto.setPrecio(new BigDecimal("10.00"));
        producto.setStockActual(20);
        producto.setStockMinimo(0);
        producto.setDisponible(true);
        return producto;
    }
}
