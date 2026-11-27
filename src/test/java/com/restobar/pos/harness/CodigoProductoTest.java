package com.restobar.pos.harness;

import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.Producto;
import com.restobar.pos.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato del código de producto serial: se genera automáticamente para productos
 * nuevos sin código, se respeta si el usuario lo escribe y no se regenera al editar.
 *
 * <p>Se sobre la configuración de test existente (H2 en memoria, perfil test y
 * DataSeeder con P001..P020). Cada caso usa nombres y códigos únicos para no colisionar
 * con el seed ni con los demás tests del harness que comparten el contexto.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"ADMIN"})
class CodigoProductoTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    void guardarProductoNuevoSinCodigo_generaCodigoSerieUnicoYContinuo() throws Exception {
        String nombre = "Producto serial " + sufijoUnico();
        long cantidadAntes = productoRepository.count();

        mockMvc.perform(post("/menu/guardar")
                        .with(csrf())
                        .param("nombre", nombre)
                        .param("categoria", CategoriaProducto.BEBIDA.name())
                        .param("precio", "12.50")
                        .param("stockActual", "10")
                        .param("stockMinimo", "0"))
                .andExpect(status().is3xxRedirection());

        String codigo = productoPorNombre(nombre).getCodigo();
        assertThat(codigo).as("El código generado debe seguir la serie Pnnn").matches("P\\d{3}");
        assertThat(codigo.length()).as("El código debe caber en la columna de 40 caracteres")
                .isLessThanOrEqualTo(40);
        assertThat(Integer.parseInt(codigo.substring(1)))
                .as("Debe continuar la serie sembrada hasta P020")
                .isGreaterThanOrEqualTo(21);
        assertThat(productoRepository.count()).isEqualTo(cantidadAntes + 1);
        assertThat(productoRepository.findAll().stream()
                .filter(producto -> codigo.equals(producto.getCodigo()))
                .count()).as("El código generado debe ser único").isEqualTo(1);
    }

    @Test
    void guardarProductoNuevoConCodigoExplicito_respetaElCodigo() throws Exception {
        String sufijo = sufijoUnico();
        String nombre = "Producto código explícito " + sufijo;
        String codigo = "EXP-" + sufijo;

        mockMvc.perform(post("/menu/guardar")
                        .with(csrf())
                        .param("codigo", codigo)
                        .param("nombre", nombre)
                        .param("categoria", CategoriaProducto.ENTRADA.name())
                        .param("precio", "8.00")
                        .param("stockActual", "5")
                        .param("stockMinimo", "1"))
                .andExpect(status().is3xxRedirection());

        assertThat(productoPorNombre(nombre).getCodigo()).isEqualTo(codigo);
    }

    @Test
    void editarProductoExistente_noRegeneraSuCodigo() throws Exception {
        String sufijo = sufijoUnico();
        String codigoOriginal = "EDI-" + sufijo;
        Producto existente = new Producto();
        existente.setCodigo(codigoOriginal);
        existente.setNombre("Producto edición " + sufijo);
        existente.setCategoria(CategoriaProducto.POSTRE);
        existente.setPrecio(new BigDecimal("9.50"));
        existente.setStockActual(7);
        existente.setStockMinimo(2);
        existente.setDisponible(true);
        existente = productoRepository.save(existente);
        long cantidadAntes = productoRepository.count();
        String nombreEditado = "Producto edición " + sufijo + " modificado";

        mockMvc.perform(post("/menu/guardar")
                        .with(csrf())
                        .param("id", existente.getId().toString())
                        .param("codigo", codigoOriginal)
                        .param("nombre", nombreEditado)
                        .param("categoria", CategoriaProducto.POSTRE.name())
                        .param("precio", "10.50")
                        .param("stockActual", "7")
                        .param("stockMinimo", "2"))
                .andExpect(status().is3xxRedirection());

        Producto recargado = productoRepository.findById(existente.getId()).orElseThrow();
        assertThat(recargado.getCodigo()).isEqualTo(codigoOriginal);
        assertThat(recargado.getNombre()).isEqualTo(nombreEditado);
        assertThat(productoRepository.count()).isEqualTo(cantidadAntes);
    }

    private Producto productoPorNombre(String nombre) {
        return productoRepository.findAll().stream()
                .filter(producto -> nombre.equals(producto.getNombre()))
                .findFirst()
                .orElseThrow();
    }

    private static String sufijoUnico() {
        return SECUENCIA.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
