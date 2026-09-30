package library_api.controller;

import library_api.dto.LoanResponse;
import library_api.dto.UserRequest;
import library_api.dto.UserResponse;
import library_api.dto.UserStatusRequest;
import library_api.service.LoanService;
import library_api.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/library/users")
public class UserController {

    private final UserService userService;
    private final LoanService loanService;

    public UserController(UserService userService, LoanService loanService) {
        this.userService = userService;
        this.loanService = loanService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest userRequest) {
        UserResponse created = userService.createUser(userRequest);
        return ResponseEntity
                .created(URI.create("/library/users/" + created.id()))
                .body(created);
    }

    /**
     * Cambia el estado del usuario. Es PATCH y no PUT porque solo se modifica una
     * parte del recurso: un PUT obligaria al cliente a reenviar el nombre y los
     * apellidos, que no cambian, con riesgo de perderlos.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UserStatusRequest statusRequest) {

        return ResponseEntity.ok(userService.updateUserStatus(id, statusRequest));
    }

    /**
     * Historial de prestamos del usuario, del mas reciente al mas antiguo.
     *
     * Vive bajo /library/users/{id}/loans y no bajo /library/loans?userId= porque
     * "lo que tiene este usuario" es una relacion del usuario, igual que en una
     * API anidada. Si el usuario no existe responde 404 en vez de una lista vacia,
     * que el cliente interpretaria como "no tiene prestamos".
     */
    @GetMapping("/{id}/loans")
    public ResponseEntity<List<LoanResponse>> getUserLoans(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(loanService.getUserLoans(id));
    }
}
