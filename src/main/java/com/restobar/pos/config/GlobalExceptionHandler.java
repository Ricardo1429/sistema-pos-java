package com.restobar.pos.config;

import com.restobar.pos.exception.ReglaNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ReglaNegocioException.class)
    public Object handleReglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
        if (esperaJson(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        return vistaError("error/regla", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public Object handleParametroInvalido(Exception ex, HttpServletRequest request) {
        // F-04: entrada invalida (tipo no convertible, parametro ausente, cuerpo ilegible)
        // es error del cliente (400), no del servidor (500). No se expone al cliente ni el
        // valor recibido ni la clase de excepcion: solo se registra en el log del servidor.
        log.warn("Entrada invalida en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (esperaJson(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Parametro invalido"));
        }
        return vistaError("error/regla", "Parámetro inválido", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        // No se exponen propertyPath, mensajes de violation ni clases: solo un mensaje generico.
        log.warn("Datos invalidos en {} {}", request.getMethod(), request.getRequestURI());
        if (esperaJson(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos inválidos"));
        }
        return vistaError("error/regla", "Datos inválidos", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenerica(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status != null && !status.is5xxServerError()) {
                if (esperaJson(request)) {
                    return ResponseEntity.status(status).body(Map.of("error", status.getReasonPhrase()));
                }
                return vistaError("error/regla", status.getReasonPhrase(), status);
            }
        }
        String mensaje = "Ha ocurrido un error inesperado";
        log.error("Error no controlado en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        if (esperaJson(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", mensaje));
        }
        return vistaError("error/regla", mensaje, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ModelAndView vistaError(String vista, String mensaje, HttpStatus status) {
        ModelAndView mav = new ModelAndView(vista);
        mav.addObject("mensaje", mensaje);
        mav.setStatus(status);
        return mav;
    }

    private boolean esperaJson(HttpServletRequest request) {
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }
}

