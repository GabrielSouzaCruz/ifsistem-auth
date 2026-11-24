package ifsuldeminas.edu.br.ifsystem.auth.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Erros da API no mesmo formato do 401/403: {"status", "erro"}. */
@RestControllerAdvice
class ErroHandler {
    @ExceptionHandler
    ResponseEntity<Map<String, Object>> tratar(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String erro = e.getReason() != null ? e.getReason() : "Erro";
        return ResponseEntity.status(status).body(Map.of("status", status, "erro", erro));
    }

    /** Duas criacoes simultaneas: a segunda so falha na constraint do banco. */
    @ExceptionHandler
    ResponseEntity<Map<String, Object>> tratar(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", 409, "erro", "CPF, e-mail ou matrícula já cadastrados"));
    }
}
