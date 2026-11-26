package com.restobar.pos.harness;

import com.restobar.pos.exception.ReglaNegocioException;
import com.restobar.pos.model.CanalOrden;
import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.DetalleOrden;
import com.restobar.pos.model.EstadoItem;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.EstadoOrden;
import com.restobar.pos.model.EstadoTurno;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Orden;
import com.restobar.pos.model.Producto;
import com.restobar.pos.model.Rol;
import com.restobar.pos.model.TurnoCaja;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.OrdenRepository;
import com.restobar.pos.repository.ProductoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Harness de verificación SDD: una prueba por regla de negocio (RN-01..RN-05)
 * más una prueba de persistencia que valida los mapeos JPA contra H2 en memoria.
 *
 * Las reglas de dominio son POJOs: se instancian directamente, sin Mockito.
 */
@SpringBootTest
class SDDVerificationHarnessTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    // ---------------------------------------------------------------------
    // RN-01 Ciclo de mesas
    // ---------------------------------------------------------------------

    @Test
    void rn01_mesaDisponiblePasaAOcupadaAlAbrirOrden() {
        Mesa mesa = nuevaMesa("RN01-M1");

        assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);

        mesa.abrirOrden();

        assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.OCUPADA);
        assertThat(mesa.estaDisponible()).isFalse();
    }

    @Test
    void rn01_mesaOcupadaNoPermiteAbrirOtraOrden() {
        Mesa mesa = nuevaMesa("RN01-M2");
        mesa.abrirOrden();

        assertThatThrownBy(mesa::abrirOrden)
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.OCUPADA);
    }

    @Test
    void rn01_cicloCompletoLiberaMesaAlPagar() {
        Mesa mesa = nuevaMesa("RN01-M3");
        mesa.abrirOrden();
        Orden orden = nuevaOrden(CanalOrden.MESA, mesa, nuevoUsuario(Rol.MESERO));
        orden.agregarProducto(nuevoProducto("RN01-P3", "15000", 10), 2);

        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.ABIERTA);
        assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.OCUPADA);

        orden.enviarACocina();
        orden.marcarLista();
        orden.procesarPagoPOS();

        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PAGADA);
        assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);
    }

    @Test
    void rn01_ordenPagadaNoPermiteSegundoPago() {
        Mesa mesa = nuevaMesa("RN01-M4");
        mesa.abrirOrden();
        Orden orden = nuevaOrden(CanalOrden.MESA, mesa, nuevoUsuario(Rol.MESERO));
        orden.agregarProducto(nuevoProducto("RN01-P4", "15000", 10), 2);
        orden.enviarACocina();
        orden.marcarLista();
        orden.procesarPagoPOS();

        assertThatThrownBy(orden::procesarPagoPOS)
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PAGADA);
    }

    // ---------------------------------------------------------------------
    // RN-02 Inventario atómico y totales
    // ---------------------------------------------------------------------

    @Test
    void rn02_agregarProductoDescuentaStockYRegistraDetalle() {
        Producto producto = nuevoProducto("RN02-P1", "15000", 10);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN02-M1"), nuevoUsuario(Rol.MESERO));

        orden.agregarProducto(producto, 3);

        assertThat(producto.getStockActual()).isEqualTo(7);
        assertThat(orden.getDetalles()).hasSize(1);
        DetalleOrden detalle = orden.getDetalles().get(0);
        assertThat(detalle.getCantidad()).isEqualTo(3);
        assertThat(detalle.subtotal()).isEqualByComparingTo("45000");
    }

    @Test
    void rn02_stockInsuficienteNoModificaStockNiDetalles() {
        Producto producto = nuevoProducto("RN02-P2", "15000", 10);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN02-M2"), nuevoUsuario(Rol.MESERO));
        orden.agregarProducto(producto, 3);

        assertThatThrownBy(() -> orden.agregarProducto(producto, 99))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(producto.getStockActual()).isEqualTo(7);
        assertThat(orden.getDetalles()).hasSize(1);
        assertThat(orden.getDetalles().get(0).getCantidad()).isEqualTo(3);
    }

    @Test
    void rn02_stockLlegaACeroYProductoQuedaNoDisponible() {
        Producto producto = nuevoProducto("RN02-P3", "5000", 2);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN02-M3"), nuevoUsuario(Rol.MESERO));

        orden.agregarProducto(producto, 2);

        assertThat(producto.getStockActual()).isZero();
        assertThat(producto.isDisponible()).isFalse();
    }

    @Test
    void rn02_totalesAplicanImpoconsumoDelOchoPorCiento() {
        Producto producto = nuevoProducto("RN02-P4", "15000", 10);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN02-M4"), nuevoUsuario(Rol.MESERO));

        orden.agregarProducto(producto, 3);

        assertThat(orden.getPropina()).isEqualByComparingTo("0");
        assertThat(orden.getSubtotal()).isEqualByComparingTo("45000");
        assertThat(orden.getImpoconsumo()).isEqualByComparingTo("3600.00");
        assertThat(orden.getTotal()).isEqualByComparingTo("48600");
        assertThat(orden.getTotal())
                .isEqualByComparingTo(orden.getSubtotal().add(orden.getImpoconsumo()));
    }

    // ---------------------------------------------------------------------
    // RN-03 Inmutabilidad de comandas
    // ---------------------------------------------------------------------

    @Test
    void rn03_itemEnCocinaNoSeAnulaConMesero() {
        Producto producto = nuevoProducto("RN03-P1", "15000", 10);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN03-M1"), nuevoUsuario(Rol.MESERO));
        DetalleOrden detalle = agregarDetalleConId(orden, producto, 2, 1L);
        detalle.setEstadoItem(EstadoItem.EN_COCINA);
        Usuario mesero = nuevoUsuario(Rol.MESERO);

        assertThatThrownBy(() -> orden.anularDetalle(1L, mesero))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(detalle.getEstadoItem()).isEqualTo(EstadoItem.EN_COCINA);
        assertThat(detalle.getAutorizadoAnulacionPor()).isNull();
        assertThat(producto.getStockActual()).isEqualTo(8);
    }

    @Test
    void rn03_supervisorAnulaItemEnCocinaYReponeStock() {
        Producto producto = nuevoProducto("RN03-P2", "15000", 10);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN03-M2"), nuevoUsuario(Rol.MESERO));
        DetalleOrden detalle = agregarDetalleConId(orden, producto, 2, 2L);
        detalle.setEstadoItem(EstadoItem.EN_COCINA);
        Usuario supervisor = nuevoUsuario(Rol.SUPERVISOR);

        orden.anularDetalle(2L, supervisor);

        assertThat(detalle.getEstadoItem()).isEqualTo(EstadoItem.ANULADO);
        assertThat(detalle.getAutorizadoAnulacionPor()).isSameAs(supervisor);
        assertThat(producto.getStockActual()).isEqualTo(10);
        assertThat(orden.getSubtotal()).isEqualByComparingTo("0");
    }

    @Test
    void rn03_itemPendienteSeAnulaConMeseroSinSupervisor() {
        Producto producto = nuevoProducto("RN03-P3", "15000", 10);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN03-M3"), nuevoUsuario(Rol.MESERO));
        DetalleOrden detalle = agregarDetalleConId(orden, producto, 4, 3L);
        Usuario mesero = nuevoUsuario(Rol.MESERO);

        orden.anularDetalle(3L, mesero);

        assertThat(detalle.getEstadoItem()).isEqualTo(EstadoItem.ANULADO);
        assertThat(detalle.getAutorizadoAnulacionPor()).isNull();
        assertThat(producto.getStockActual()).isEqualTo(10);
    }

    @Test
    void rn03_detalleInexistenteLanzaReglaNegocio() {
        Producto producto = nuevoProducto("RN03-P4", "15000", 10);
        Orden orden = nuevaOrden(CanalOrden.MESA, nuevaMesa("RN03-M4"), nuevoUsuario(Rol.MESERO));
        DetalleOrden detalle = agregarDetalleConId(orden, producto, 2, 4L);

        assertThatThrownBy(() -> orden.anularDetalle(9999L, nuevoUsuario(Rol.SUPERVISOR)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Detalle no encontrado");

        assertThat(detalle.getEstadoItem()).isEqualTo(EstadoItem.PENDIENTE);
        assertThat(producto.getStockActual()).isEqualTo(8);
    }

    // ---------------------------------------------------------------------
    // RN-04 Delivery apps
    // ---------------------------------------------------------------------

    @Test
    void rn04_rappiSinCodigoExternoLanza() {
        Orden orden = nuevaOrden(CanalOrden.RAPPI, null, null);

        assertThatThrownBy(orden::validarCanal)
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rn04_uberEatsConCodigoSinMesaEsValido() {
        Orden orden = nuevaOrden(CanalOrden.UBER_EATS, null, null);
        orden.setCodigoExternoDelivery("UB-123");

        assertThatCode(orden::validarCanal).doesNotThrowAnyException();
    }

    @Test
    void rn04_didiFoodConMesaAsociadaLanza() {
        Orden orden = nuevaOrden(CanalOrden.DIDI_FOOD, nuevaMesa("RN04-M1"), null);
        orden.setCodigoExternoDelivery("DIDI-9");

        assertThatThrownBy(orden::validarCanal)
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rn04_canalMesaSinMesaAsignadaLanza() {
        Orden orden = nuevaOrden(CanalOrden.MESA, null, nuevoUsuario(Rol.MESERO));

        assertThatThrownBy(orden::validarCanal)
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ---------------------------------------------------------------------
    // RN-05 Cierre de caja estricto
    // ---------------------------------------------------------------------

    @Test
    void rn05_noCierraTurnoConMesaOcupada() {
        Usuario cajero = nuevoUsuario(Rol.CAJERO);
        TurnoCaja turno = nuevoTurno(cajero, "100000");
        Mesa mesa = nuevaMesa("RN05-M1");
        mesa.abrirOrden();

        assertThatThrownBy(() -> turno.cerrarTurno(new BigDecimal("100000"), List.of(mesa), cajero))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.ABIERTO);
        assertThat(turno.getFechaCierre()).isNull();
    }

    @Test
    void rn05_noCierraTurnoConMesaPorCobrar() {
        Usuario cajero = nuevoUsuario(Rol.CAJERO);
        TurnoCaja turno = nuevoTurno(cajero, "100000");
        Mesa mesa = nuevaMesa("RN05-M2");
        mesa.abrirOrden();
        mesa.marcarPorCobrar();

        assertThatThrownBy(() -> turno.cerrarTurno(new BigDecimal("100000"), List.of(mesa), cajero))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.ABIERTO);
    }

    @Test
    void rn05_cierraTurnoCuadradoSinSupervisor() {
        Usuario cajero = nuevoUsuario(Rol.CAJERO);
        TurnoCaja turno = nuevoTurno(cajero, "100000");

        turno.cerrarTurno(new BigDecimal("100000"), List.of(), cajero);

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.CERRADO);
        assertThat(turno.getFechaCierre()).isNotNull();
        assertThat(turno.getEfectivoCierreDeclarado()).isEqualByComparingTo("100000");
    }

    @Test
    void rn05_descuadreConCajeroLanzaYNoCierra() {
        Usuario cajero = nuevoUsuario(Rol.CAJERO);
        TurnoCaja turno = nuevoTurno(cajero, "100000");
        turno.setTotalVentasEfectivo(new BigDecimal("50000"));

        assertThatThrownBy(() -> turno.cerrarTurno(new BigDecimal("140000"), List.of(), cajero))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.ABIERTO);
        assertThat(turno.getFechaCierre()).isNull();
        assertThat(turno.getEfectivoCierreDeclarado()).isNull();
    }

    @Test
    void rn05_descuadreConSupervisorCierra() {
        Usuario supervisor = nuevoUsuario(Rol.SUPERVISOR);
        TurnoCaja turno = nuevoTurno(supervisor, "100000");
        turno.setTotalVentasEfectivo(new BigDecimal("50000"));

        turno.cerrarTurno(new BigDecimal("140000"), List.of(), supervisor);

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.CERRADO);
        assertThat(turno.getFechaCierre()).isNotNull();
        assertThat(turno.getEfectivoCierreDeclarado()).isEqualByComparingTo("140000");
    }

    // ---------------------------------------------------------------------
    // Persistencia JPA contra H2
    // ---------------------------------------------------------------------

    @Test
    @Transactional
    void persistencia_orden_con_detalles() throws Exception {
        try (Connection conexion = dataSource.getConnection()) {
            assertThat(conexion.getMetaData().getURL()).contains("jdbc:h2:mem:restobar_test");
        }

        Mesa mesa = nuevaMesa("PERS-M1");
        mesa.abrirOrden();
        mesaRepository.save(mesa);

        Producto plato = nuevoProducto("PERS-P1", "20000", 10);
        Producto bebida = nuevoProducto("PERS-P2", "5000", 5);
        productoRepository.save(plato);
        productoRepository.save(bebida);

        Orden orden = nuevaOrden(CanalOrden.MESA, mesa, null);
        orden.agregarProducto(plato, 2);
        orden.agregarProducto(bebida, 1);

        Orden guardada = ordenRepository.save(orden);
        entityManager.flush();
        Long id = guardada.getId();
        assertThat(id).isNotNull();
        entityManager.clear();

        Orden recuperada = ordenRepository.findById(id).orElseThrow();

        assertThat(recuperada.getDetalles()).hasSize(2);
        assertThat(recuperada.getNumeroOrden()).isNotNull().isNotBlank().startsWith("ORD-");
        assertThat(recuperada.getSubtotal()).isNotNull();
        assertThat(recuperada.getImpoconsumo()).isNotNull();
        assertThat(recuperada.getTotal()).isNotNull();
        assertThat(recuperada.getSubtotal()).isEqualByComparingTo("45000");
        assertThat(recuperada.getImpoconsumo()).isEqualByComparingTo("3600.00");
        assertThat(recuperada.getTotal()).isEqualByComparingTo("48600");
        assertThat(recuperada.getCreatedAt()).isNotNull();
        assertThat(recuperada.getDetalles())
                .extracting(detalle -> detalle.getProducto().getCodigo())
                .containsExactlyInAnyOrder("PERS-P1", "PERS-P2");
    }

    // ---------------------------------------------------------------------
    // Helpers de fábrica (POJOs, sin contexto ni mocks)
    // ---------------------------------------------------------------------

    private static Usuario nuevoUsuario(Rol rol) {
        int n = SECUENCIA.incrementAndGet();
        Usuario usuario = new Usuario();
        usuario.setNombre("Usuario " + rol + " " + n);
        usuario.setUsername("user_" + rol.name().toLowerCase() + "_" + n);
        usuario.setPassword("secreto-test");
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
    }

    private static Mesa nuevaMesa(String numero) {
        Mesa mesa = new Mesa();
        mesa.setNumero(numero);
        mesa.setZona(ZonaMesa.SALON);
        mesa.setCapacidad(4);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        return mesa;
    }

    private static Producto nuevoProducto(String codigo, String precio, int stock) {
        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre("Producto " + codigo);
        producto.setCategoria(CategoriaProducto.PLATO_FUERTE);
        producto.setPrecio(new BigDecimal(precio));
        producto.setStockActual(stock);
        producto.setStockMinimo(0);
        producto.setDisponible(true);
        return producto;
    }

    private static Orden nuevaOrden(CanalOrden canal, Mesa mesa, Usuario mesero) {
        Orden orden = new Orden();
        orden.setCanal(canal);
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        return orden;
    }

    private static DetalleOrden agregarDetalleConId(Orden orden, Producto producto, int cantidad, Long detalleId) {
        orden.agregarProducto(producto, cantidad);
        DetalleOrden detalle = orden.getDetalles().get(orden.getDetalles().size() - 1);
        detalle.setId(detalleId);
        return detalle;
    }

    private static TurnoCaja nuevoTurno(Usuario cajero, String baseInicial) {
        TurnoCaja turno = new TurnoCaja();
        turno.setCajero(cajero);
        turno.setFechaApertura(LocalDateTime.now().minusHours(2));
        turno.setBaseInicial(new BigDecimal(baseInicial));
        return turno;
    }
}
