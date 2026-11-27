package com.restobar.pos.controller;

import com.restobar.pos.exception.ReglaNegocioException;
import com.restobar.pos.model.CategoriaProducto;
import com.restobar.pos.model.Producto;
import com.restobar.pos.repository.ProductoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/menu")
public class MenuController {

    private static final Pattern CODIGO_SERIE = Pattern.compile("^P(\\d{3})$");
    private static final int ULTIMO_CODIGO_SERIE = 999;

    private final ProductoRepository productoRepository;

    public MenuController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * Impide que la request fije campos de control de la entidad (versionado optimista).
     * La edicion sigue funcionando porque el {@code version} vigente es el de la entidad
     * cargada desde la base de datos, nunca el enviado por el cliente.
     */
    @InitBinder("producto")
    public void initProductoBinder(WebDataBinder binder) {
        binder.setDisallowedFields("version");
    }

    @GetMapping("")
    public String listar(@RequestParam(required = false) CategoriaProducto categoria, Model model) {
        List<Producto> productos = categoria == null
                ? productoRepository.findAllByOrderByNombreAsc()
                : productoRepository.findByCategoria(categoria).stream()
                        .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                        .toList();

        model.addAttribute("productos", productos);
        model.addAttribute("categorias", CategoriaProducto.values());
        model.addAttribute("categoriaSeleccionada", categoria != null ? categoria.name() : null);
        return "menu/listar";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("categorias", CategoriaProducto.values());
        model.addAttribute("edicion", false);
        return "menu/formulario";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("producto", producto);
        model.addAttribute("categorias", CategoriaProducto.values());
        model.addAttribute("edicion", true);
        return "menu/formulario";
    }

    @PostMapping("/guardar")
    @Transactional
    public String guardar(@ModelAttribute("producto") @Valid Producto producto,
                          BindingResult bindingResult,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        asignarCodigoSiFalta(producto);
        if (!bindingResult.hasErrors() && codigoDuplicado(producto)) {
            bindingResult.rejectValue("codigo", "duplicado", "El código ya existe");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("categorias", CategoriaProducto.values());
            model.addAttribute("edicion", producto.getId() != null);
            return "menu/formulario";
        }
        if (producto.getId() == null) {
            productoRepository.save(producto);
        } else {
            Producto existente = productoRepository.findById(producto.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            existente.setCodigo(producto.getCodigo());
            existente.setNombre(producto.getNombre());
            existente.setDescripcion(producto.getDescripcion());
            existente.setCategoria(producto.getCategoria());
            existente.setPrecio(producto.getPrecio());
            existente.setStockActual(producto.getStockActual());
            existente.setStockMinimo(producto.getStockMinimo());
            existente.setDisponible(producto.isDisponible());
            existente.setImagenUrl(producto.getImagenUrl());
            productoRepository.save(existente);
        }
        redirectAttributes.addFlashAttribute("exito", "Producto guardado");
        return "redirect:/menu";
    }

    @PostMapping("/{id}/eliminar")
    @Transactional
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Producto producto = buscarProducto(id);
        productoRepository.delete(producto);
        redirectAttributes.addFlashAttribute("exito", "Producto " + producto.getNombre() + " eliminado");
        return "redirect:/menu";
    }

    @PostMapping("/{id}/reponer")
    @Transactional
    public String reponer(@PathVariable Long id,
                          @RequestParam int cantidad,
                          RedirectAttributes redirectAttributes) {
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser mayor a cero");
        }
        Producto producto = buscarProducto(id);
        producto.reponerStock(cantidad);
        productoRepository.save(producto);
        redirectAttributes.addFlashAttribute("exito", "Stock de " + producto.getNombre() + " repuesto");
        return "redirect:/menu";
    }

    @PostMapping("/{id}/disponibilidad")
    @Transactional
    public String alternarDisponibilidad(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Producto producto = buscarProducto(id);
        producto.setDisponible(!producto.isDisponible());
        productoRepository.save(producto);
        redirectAttributes.addFlashAttribute("exito", "Disponibilidad de " + producto.getNombre() + " actualizada");
        return "redirect:/menu";
    }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void asignarCodigoSiFalta(Producto producto) {
        if (producto.getId() != null) {
            return;
        }
        String codigo = producto.getCodigo();
        if (codigo != null && !codigo.isBlank()) {
            return;
        }
        int numero = productoRepository.findAll().stream()
                .map(Producto::getCodigo)
                .filter(Objects::nonNull)
                .map(CODIGO_SERIE::matcher)
                .filter(Matcher::matches)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElse(0) + 1;
        for (; numero <= ULTIMO_CODIGO_SERIE; numero++) {
            String candidato = String.format("P%03d", numero);
            if (!productoRepository.existsByCodigo(candidato)) {
                producto.setCodigo(candidato);
                return;
            }
        }
        throw new ReglaNegocioException("No hay códigos de producto disponibles");
    }

    private boolean codigoDuplicado(Producto producto) {
        if (producto.getCodigo() == null || producto.getCodigo().isBlank()) {
            return false;
        }
        if (producto.getId() == null) {
            return productoRepository.existsByCodigo(producto.getCodigo());
        }
        return productoRepository.findById(producto.getId())
                .map(existente -> !producto.getCodigo().equals(existente.getCodigo())
                        && productoRepository.existsByCodigo(producto.getCodigo()))
                .orElse(false);
    }
}
