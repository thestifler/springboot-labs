package library_api.controller;

import java.util.List;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import library_api.dto.LoanRequest;
import library_api.dto.LoanResponse;
import library_api.service.LoanService;

/**
 * Endpoints del recurso /library/loans.
 *
 * No contiene logica de negocio: valida el formato de la entrada, delega en
 * LoanService y traduce el resultado a HTTP. Los errores de dominio (404 y 409) y
 * los de validacion (400) los resuelve GlobalExceptionHandler.
 *
 * Devolver y renovar son POST y no PUT/PATCH porque no son una actualizacion
 * parcial del recurso: appenden un hecho al historial (el libro vuelve, el plazo
 * se prorroga). Un PUT sobre /library/loans/{id} seria ambiguo con "actualizar el
 * prestamo" y ademas obligaria al cliente a conocer el estado completo.
 *
 * Los verbos van en la URL (/return, /renew) y no en el cuerpo porque la peticion
 * no lleva datos: un POST con cuerpo vacio a /library/loans/{id} seria
 * indescriminable de un alta.
 */
@RestController
@RequestMapping("/library/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    /**
     * Presta un libro a un usuario. 201 Created con el Location del prestamo
     * creado, igual que en el alta de libros y de usuarios.
     */
    @PostMapping
    public ResponseEntity<LoanResponse> lendBook(@Valid @RequestBody LoanRequest loanRequest) {
        LoanResponse created = loanService.lendBook(loanRequest);
        return ResponseEntity
                .created(URI.create("/library/loans/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanResponse> getLoan(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(loanService.getLoan(id));
    }

    /**
     * Registra la devolucion y reingresa el ejemplar en el inventario. 200 con el
     * prestamo ya cerrado, para que el cliente vea la fecha de devolucion
     * registrada sin tener que consultarlo otra vez.
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<LoanResponse> returnBook(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(loanService.returnBook(id));
    }

    /**
     * Prorroga el plazo del prestamo. 200 con la nueva fecha limite.
     */
    @PostMapping("/{id}/renew")
    public ResponseEntity<LoanResponse> renewLoan(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(loanService.renewLoan(id));
    }

    /**
     * Prestamos vencidos, del mas retrasado al mas reciente.
     *
     * El orden lo pone la consulta, no un sort en memoria. En cuanto este endpoint
     * crezca habra que paginarlo, y la forma de hacerlo sin tocar el servicio es
     * empezar ya a paginar en SQL en lugar de traer la tabla entera.
     */
    @GetMapping("/overdue")
    public ResponseEntity<List<LoanResponse>> getOverdueLoans() {
        return ResponseEntity.ok(loanService.getOverdueLoans());
    }
}
