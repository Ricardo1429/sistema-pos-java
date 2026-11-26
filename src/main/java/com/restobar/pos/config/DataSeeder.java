package com.restobar.pos.config;

import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.EstadoMesa;
import com.restobar.pos.model.Mesa;
import com.restobar.pos.model.Producto;
import com.restobar.pos.model.Rol;
import com.restobar.pos.model.Usuario;
import com.restobar.pos.model.ZonaMesa;
import com.restobar.pos.repository.MesaRepository;
import com.restobar.pos.repository.ProductoRepository;
import com.restobar.pos.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final MesaRepository mesaRepository;
    private final ProductoRepository productoRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      MesaRepository mesaRepository,
                      ProductoRepository productoRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.password}") String seedPassword) {
        this.usuarioRepository = usuarioRepository;
        this.mesaRepository = mesaRepository;
        this.productoRepository = productoRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        String password = passwordEncoder.encode(seedPassword);

        List<Usuario> usuarios = List.of(
                nuevoUsuario("Administrador", "admin", password, Rol.ADMIN),
                nuevoUsuario("Juan Mesero", "mesero1", password, Rol.MESERO),
                nuevoUsuario("Ana Cocina", "cocina1", password, Rol.COCINERO_BARMAN),
                nuevoUsuario("Carlos Cajero", "cajero1", password, Rol.CAJERO),
                nuevoUsuario("Marta Supervisora", "supervisor1", password, Rol.SUPERVISOR));

        List<Mesa> mesas = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            ZonaMesa zona = i <= 6 ? ZonaMesa.SALON : i <= 10 ? ZonaMesa.TERRAZA : ZonaMesa.BARRA;
            int capacidad = i <= 3 ? 2 : i <= 8 ? 4 : 6;
            mesas.add(nuevaMesa(String.valueOf(i), zona, capacidad));
        }

        List<Producto> productos = List.of(
                nuevoProducto("P001", "Bandeja Paisa", CategoriaProducto.PLATO_FUERTE, "42000", 40),
                nuevoProducto("P002", "Ajiaco Santafereño", CategoriaProducto.PLATO_FUERTE, "38000", 35),
                nuevoProducto("P003", "Ceviche de Camarón", CategoriaProducto.ENTRADA, "32000", 25),
                nuevoProducto("P004", "Limonada de Coco", CategoriaProducto.BEBIDA, "12000", 80),
                nuevoProducto("P005", "Mojito Cubano", CategoriaProducto.COCTEL, "28000", 45),
                nuevoProducto("P006", "Postre de Natas", CategoriaProducto.POSTRE, "14000", 30),
                nuevoProducto("P007", "Cerveza Artesanal", CategoriaProducto.BEBIDA, "15000", 100),
                nuevoProducto("P008", "Hamburguesa Restobar", CategoriaProducto.PLATO_FUERTE, "34000", 50),
                nuevoProducto("P009", "Arepa de Huevo", CategoriaProducto.ENTRADA, "9000", 60),
                nuevoProducto("P010", "Agua de Panela con Limón", CategoriaProducto.BEBIDA, "6000", 90),
                nuevoProducto("P011", "Piña Colada", CategoriaProducto.COCTEL, "26000", 40),
                nuevoProducto("P012", "Flan de Caramelo", CategoriaProducto.POSTRE, "13000", 25),
                nuevoProducto("P013", "Churrasco con Chimichurri", CategoriaProducto.PLATO_FUERTE, "55000", 20),
                nuevoProducto("P014", "Patacones con Hogao", CategoriaProducto.ENTRADA, "11000", 55),
                nuevoProducto("P015", "Michelada", CategoriaProducto.COCTEL, "18000", 35),
                nuevoProducto("P016", "Café Campesino", CategoriaProducto.BEBIDA, "7000", 100),
                nuevoProducto("P017", "Tres Leches", CategoriaProducto.POSTRE, "15000", 18),
                nuevoProducto("P018", "Trucha al Ajillo", CategoriaProducto.PLATO_FUERTE, "48000", 15),
                nuevoProducto("P019", "Alitas BBQ", CategoriaProducto.ENTRADA, "24000", 40),
                nuevoProducto("P020", "Whisky en las Rocas", CategoriaProducto.COCTEL, "85000", 5));

        usuarioRepository.saveAll(usuarios);
        mesaRepository.saveAll(mesas);
        productoRepository.saveAll(productos);
    }

    private Usuario nuevoUsuario(String nombre, String username, String password, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setUsername(username);
        usuario.setPassword(password);
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
    }

    private Mesa nuevaMesa(String numero, ZonaMesa zona, int capacidad) {
        Mesa mesa = new Mesa();
        mesa.setNumero(numero);
        mesa.setZona(zona);
        mesa.setCapacidad(capacidad);
        mesa.setEstado(EstadoMesa.DISPONIBLE);
        return mesa;
    }

    private Producto nuevoProducto(String codigo, String nombre, CategoriaProducto categoria,
                                   String precio, int stockActual) {
        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre(nombre);
        producto.setCategoria(categoria);
        producto.setPrecio(new BigDecimal(precio));
        producto.setStockActual(stockActual);
        producto.setStockMinimo(5);
        producto.setDisponible(true);
        producto.setImagenUrl(null);
        return producto;
    }
}
