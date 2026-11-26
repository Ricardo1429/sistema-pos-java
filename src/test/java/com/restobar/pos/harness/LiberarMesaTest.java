package com.restobar.pos.harness;

import com.restobar.pos.model.CanalOrden;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.UsuarioRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de visibilidad del botón "Liberar" en el listado de mesas.
 *
 * <p>Cada caso crea su propia mesa con número único para no colisionar con el seed
 * ni con otros tests del harness que comparten el contexto H2.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"ADMIN"})
class LiberarMesaTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final String MENSAJE_ORDEN_ACTIVA = "La mesa tiene una orden activa";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @Transactional
    void listar_mesaConOrdenAbierta_noMuestraBotonLiberar() throws Exception {
        Mesa mesa = guardarMesaOcupada();
        guardarOrden(mesa, EstadoOrden.ABIERTA);
        inicializarDetallesDeOrdenesAbiertas();

        String html = mockMvc.perform(get("/mesas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("Una mesa con orden ABIERTA no debe ofrecer una acción que el servidor rechaza")
                .doesNotContain(rutaLiberar(mesa));
    }

    @Test
    @Transactional
    void listar_mesaPorCobrarSinOrdenActiva_muestraBotonLiberar() throws Exception {
        Mesa mesa = guardarMesa(EstadoMesa.POR_COBRAR);
        assertThat(ordenRepository.findByMesaId(mesa.getId())).isEmpty();
        inicializarDetallesDeOrdenesAbiertas();

        String html = mockMvc.perform(get("/mesas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("Una mesa POR_COBRAR sin orden activa sí puede liberarse")
                .contains(rutaLiberar(mesa));
    }

    @Test
    void liberar_conOrdenActiva_devuelve400YNoLibera() throws Exception {
        Mesa mesa = guardarMesaOcupada();
        guardarOrden(mesa, EstadoOrden.ABIERTA);

        mockMvc.perform(post("/mesas/{id}/liberar", mesa.getId())
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(MENSAJE_ORDEN_ACTIVA));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .as("Una mesa con orden activa no debe liberarse")
                .isEqualTo(EstadoMesa.OCUPADA);
    }

    private void inicializarDetallesDeOrdenesAbiertas() {
        // open-in-view=false en tests: el listado recorre Orden.detalles (lazy).
        ordenRepository.findByEstado(EstadoOrden.ABIERTA)
                .forEach(orden -> orden.getDetalles().size());
    }

    private String rutaLiberar(Mesa mesa) {
        return "/mesas/" + mesa.getId() + "/liberar";
    }

    private Mesa guardarMesa(EstadoMesa estado) {
        Mesa mesa = new Mesa();
        mesa.setNumero("LM-" + SECUENCIA.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 6));
        mesa.setZona(ZonaMesa.SALON);
        mesa.setCapacidad(4);
        mesa.setEstado(estado);
        return mesaRepository.save(mesa);
    }

    private Mesa guardarMesaOcupada() {
        Mesa mesa = guardarMesa(EstadoMesa.DISPONIBLE);
        mesa.abrirOrden();
        return mesaRepository.save(mesa);
    }

    private Orden guardarOrden(Mesa mesa, EstadoOrden estado) {
        Usuario mesero = usuarioRepository.findByUsername("admin").orElseThrow();
        Orden orden = new Orden();
        orden.setCanal(CanalOrden.MESA);
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setEstado(estado);
        return ordenRepository.save(orden);
    }
}
