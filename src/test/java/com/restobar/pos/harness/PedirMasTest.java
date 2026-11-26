package com.restobar.pos.harness;

import com.restobar.pos.model.CanalOrden;
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
 * Contrato del flujo "pedir más" sobre una mesa ya ocupada.
 *
 * <p>Se sobre la configuracion de test existente (H2 en memoria, perfil test y
 * DataSeeder). Cada caso crea su propia mesa con numero unico para no colisionar
 * con las 12 mesas sembradas ni con otros tests del harness.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"ADMIN"})
class PedirMasTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final Pattern REDIRECT_POS = Pattern.compile("^/ordenes/(\\d+)/pos$");

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
    void pedirMas_sobreMesaOcupada_creaNuevaOrdenAbiertaYDejaLaAnteriorIntacta() throws Exception {
        Mesa mesa = guardarMesa();
        mesa.abrirOrden();
        mesa = mesaRepository.save(mesa);

        Usuario mesero = usuarioRepository.findByUsername("admin").orElseThrow();
        Producto producto = productoRepository.findByDisponibleTrue().get(0);

        Orden ordenAnterior = new Orden();
        ordenAnterior.setCanal(CanalOrden.MESA);
        ordenAnterior.setMesa(mesa);
        ordenAnterior.setMesero(mesero);
        ordenAnterior.setEstado(EstadoOrden.ABIERTA);
        ordenAnterior.agregarProducto(producto, 1);
        ordenAnterior.enviarACocina();
        Orden anteriorGuardada = ordenRepository.save(ordenAnterior);

        assertThat(anteriorGuardada.getEstado()).isEqualTo(EstadoOrden.EN_PREPARACION);
        assertThat(anteriorGuardada.getSubtotal()).isGreaterThan(BigDecimal.ZERO);

        String redirect = mockMvc.perform(post("/mesas/{id}/pedir-mas", mesa.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        Long nuevoId = idDesdeRedirect(redirect);
        assertThat(nuevoId).isNotEqualTo(anteriorGuardada.getId());

        Orden nuevaOrden = ordenRepository.findById(nuevoId).orElseThrow();
        Orden anteriorRecargada = ordenRepository.findById(anteriorGuardada.getId()).orElseThrow();
        Mesa mesaRecargada = mesaRepository.findById(mesa.getId()).orElseThrow();

        assertThat(nuevaOrden.getEstado()).isEqualTo(EstadoOrden.ABIERTA);
        assertThat(nuevaOrden.getCanal()).isEqualTo(CanalOrden.MESA);
        assertThat(nuevaOrden.getMesa()).isNotNull();
        assertThat(nuevaOrden.getMesa().getId()).isEqualTo(mesa.getId());
        assertThat(nuevaOrden.getMesero()).isNotNull();
        assertThat(nuevaOrden.getMesero().getUsername()).isEqualTo("admin");

        assertThat(anteriorRecargada.getEstado()).isEqualTo(EstadoOrden.EN_PREPARACION);
        assertThat(anteriorRecargada.getSubtotal()).isGreaterThan(BigDecimal.ZERO);
        assertThat(mesaRecargada.getEstado()).isEqualTo(EstadoMesa.OCUPADA);
        assertThat(ordenRepository.findByMesaId(mesa.getId())).hasSize(2);
    }

    @Test
    void pedirMas_sobreMesaDisponible_devuelveErrorControlado() throws Exception {
        Mesa mesa = guardarMesa();
        assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);

        // El handler responde 400 con JSON {"error": ...} cuando la peticion es AJAX.
        mockMvc.perform(post("/mesas/{id}/pedir-mas", mesa.getId())
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Abra la mesa antes de pedir"));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.DISPONIBLE);
        assertThat(ordenRepository.findByMesaId(mesa.getId())).isEmpty();
    }

    @Test
    void crearOrdenMesa_sinMesaId_devuelveErrorControlado() throws Exception {
        long ordenesAntes = ordenRepository.count();

        String cuerpo = mockMvc.perform(post("/ordenes/nueva")
                        .with(csrf())
                        .param("canal", "MESA")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Seleccione una mesa para el canal MESA"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo)
                .doesNotContain("password", "Exception", "stack", "jdbc:", "SEED", "null", "class ", "@");
        assertThat(ordenRepository.count()).isEqualTo(ordenesAntes);
    }

    @Test
    void crearOrdenMesa_conMesaId_disponible_abreMesaYCreaOrden() throws Exception {
        Mesa mesa = guardarMesa();

        String redirect = mockMvc.perform(post("/ordenes/nueva")
                        .with(csrf())
                        .param("canal", "MESA")
                        .param("mesaId", mesa.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        Orden nuevaOrden = ordenRepository.findById(idDesdeRedirect(redirect)).orElseThrow();
        Mesa mesaRecargada = mesaRepository.findById(mesa.getId()).orElseThrow();

        assertThat(nuevaOrden.getEstado()).isEqualTo(EstadoOrden.ABIERTA);
        assertThat(nuevaOrden.getCanal()).isEqualTo(CanalOrden.MESA);
        assertThat(nuevaOrden.getMesa()).isNotNull();
        assertThat(nuevaOrden.getMesa().getId()).isEqualTo(mesa.getId());
        assertThat(nuevaOrden.getMesero()).isNotNull();
        assertThat(nuevaOrden.getMesero().getUsername()).isEqualTo("admin");
        assertThat(mesaRecargada.getEstado()).isEqualTo(EstadoMesa.OCUPADA);
    }

    private Mesa guardarMesa() {
        Mesa mesa = new Mesa();
        mesa.setNumero("PM-" + SECUENCIA.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 6));
        mesa.setZona(ZonaMesa.SALON);
        mesa.setCapacidad(4);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        return mesaRepository.save(mesa);
    }

    private static Long idDesdeRedirect(String redirect) {
        assertThat(redirect).as("Se esperaba un redirect a /ordenes/{id}/pos").isNotNull();
        Matcher matcher = REDIRECT_POS.matcher(redirect);
        assertThat(matcher.matches()).as("Redirect inesperado: %s", redirect).isTrue();
        return Long.valueOf(matcher.group(1));
    }
}
