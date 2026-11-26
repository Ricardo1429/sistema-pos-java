package com.restobar.pos.harness;

import com.restobar.pos.model.CanalOrden;
import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.Producto;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.DetalleOrdenRepository;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.ProductoRepository;
import com.restobar.pos.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato del cierre de mesa sin consumo y de la página controlada de acceso denegado.
 *
 * <p>Cada caso crea sus propias entidades con identificadores únicos para no colisionar
 * con el seed ni con otros tests del harness que comparten el contexto H2.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CerrarMesaTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final String MENSAJE_CONSUMO = "La mesa tiene consumo; use Por cobrar";
    private static final String MENSAJE_DISPONIBLE = "La mesa ya está disponible";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private DetalleOrdenRepository detalleOrdenRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cerrarSinConsumo_ordenAbiertaVacia_liberaMesaYAnulaOrden() throws Exception {
        Mesa mesa = guardarMesaOcupada();
        Orden orden = guardarOrdenAbierta(mesa);
        assertThat(detalleOrdenRepository.findByOrdenId(orden.getId())).isEmpty();

        mockMvc.perform(post("/mesas/{id}/cerrar-sin-consumo", mesa.getId())
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mesas"));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .as("La mesa debe quedar disponible tras cerrar sin consumo")
                .isEqualTo(EstadoMesa.DISPONIBLE);
        assertThat(ordenRepository.findById(orden.getId()).orElseThrow().getEstado())
                .as("La orden abierta vacía debe quedar anulada")
                .isEqualTo(EstadoOrden.ANULADA);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cerrarSinConsumo_conConsumo_devuelve400YNoLibera() throws Exception {
        Mesa mesa = guardarMesaOcupada();
        Orden orden = guardarOrdenAbierta(mesa);
        Producto producto = guardarProducto();
        orden.agregarProducto(producto, 1);
        ordenRepository.save(orden);
        productoRepository.save(producto);
        assertThat(detalleOrdenRepository.findByOrdenId(orden.getId())).isNotEmpty();

        mockMvc.perform(post("/mesas/{id}/cerrar-sin-consumo", mesa.getId())
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(MENSAJE_CONSUMO));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .as("Una mesa con consumo no debe liberarse")
                .isEqualTo(EstadoMesa.OCUPADA);
        assertThat(ordenRepository.findById(orden.getId()).orElseThrow().getEstado())
                .as("La orden con consumo debe permanecer abierta")
                .isEqualTo(EstadoOrden.ABIERTA);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void cerrarSinConsumo_mesaDisponible_devuelve400() throws Exception {
        Mesa mesa = guardarMesa();
        assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);

        String cuerpo = mockMvc.perform(post("/mesas/{id}/cerrar-sin-consumo", mesa.getId())
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(MENSAJE_DISPONIBLE))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("Whitelabel Error Page");
        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.DISPONIBLE);
    }

    @Test
    @WithMockUser(username = "mesero1", roles = {"MESERO"})
    void accesoDenegado_meseroACaja_devuelveVistaControladaNoWhitelabel() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/caja")).andReturn();
        MockHttpServletResponse respuesta = resultado.getResponse();

        if (respuesta.getStatus() == 302) {
            assertThat(respuesta.getRedirectedUrl())
                    .as("accessDeniedPage debe enviar a la vista controlada")
                    .isEqualTo("/error/acceso");
        } else {
            assertThat(respuesta.getStatus())
                    .as("Se esperaba 302 a /error/acceso o 403 controlado")
                    .isEqualTo(403);
            assertThat(respuesta.getContentAsString()).doesNotContain("Whitelabel Error Page");
        }

        String cuerpoAcceso = mockMvc.perform(get("/error/acceso"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpoAcceso)
                .as("La vista /error/acceso debe existir y ser legible")
                .contains("No tienes permiso")
                .doesNotContain("Whitelabel Error Page");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void csrfInvalidoEnPost_devuelveRedirectControladoSin405() throws Exception {
        mockMvc.perform(post("/caja/turno/abrir")
                        .param("baseInicial", "0")
                        .with(csrf().useInvalidToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/acceso"));
    }

    private Mesa guardarMesa() {
        Mesa mesa = new Mesa();
        mesa.setNumero("CM-" + SECUENCIA.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 6));
        mesa.setZona(ZonaMesa.SALON);
        mesa.setCapacidad(4);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        return mesaRepository.save(mesa);
    }

    private Mesa guardarMesaOcupada() {
        Mesa mesa = guardarMesa();
        mesa.abrirOrden();
        return mesaRepository.save(mesa);
    }

    private Orden guardarOrdenAbierta(Mesa mesa) {
        Usuario mesero = usuarioRepository.findByUsername("admin").orElseThrow();
        Orden orden = new Orden();
        orden.setCanal(CanalOrden.MESA);
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setEstado(EstadoOrden.ABIERTA);
        return ordenRepository.save(orden);
    }

    private Producto guardarProducto() {
        String sufijo = SECUENCIA.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6);
        Producto producto = new Producto();
        producto.setCodigo("CMP-" + sufijo);
        producto.setNombre("Producto cierresinconsumo " + sufijo);
        producto.setCategoria(CategoriaProducto.PLATO_FUERTE);
        producto.setPrecio(new BigDecimal("12.50"));
        producto.setStockActual(20);
        producto.setStockMinimo(0);
        producto.setDisponible(true);
        return productoRepository.save(producto);
    }
}
