package com.moneyflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final SecurityConfig.JwtService jwt;
    AuthController(JdbcTemplate db, PasswordEncoder encoder, SecurityConfig.JwtService jwt) { this.db=db; this.encoder=encoder; this.jwt=jwt; }
    record Credentials(@Email @NotBlank @Size(max=254) String email, @NotBlank @Size(min=10,max=72) String password) {}
    @PostMapping("/register") @Transactional Map<String,Object> register(@Valid @RequestBody Credentials c) {
        validatePasswordBytes(c.password());
        String email=c.email().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            Long id=db.queryForObject("INSERT INTO app_user(email,password_hash) VALUES (?,?) RETURNING id", Long.class, email, encoder.encode(c.password()));
            db.update("INSERT INTO wallet(user_id,name,currency,balance_minor) VALUES (?,?,?,0)",id,"Main wallet","EUR");
            return Map.of("token",jwt.create(id,email),"userId",id,"email",email);
        } catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists"); }
    }
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Credentials c) {
        validatePasswordBytes(c.password());
        var users=db.query("SELECT id,email,password_hash FROM app_user WHERE email=?",(rs,n)->new User(rs.getLong("id"),rs.getString("email"),rs.getString("password_hash")),c.email().trim().toLowerCase(java.util.Locale.ROOT));
        if(users.isEmpty() || !encoder.matches(c.password(),users.get(0).hash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        User u=users.get(0); return Map.of("token",jwt.create(u.id(),u.email()),"userId",u.id(),"email",u.email());
    }
    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes for BCrypt");
        }
    }
    record User(long id,String email,String hash) {}
}
