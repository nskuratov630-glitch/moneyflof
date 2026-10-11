package com.moneyflow.api;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        return http.csrf(c -> c.disable()).formLogin(f -> f.disable()).httpBasic(b -> b.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/**", "/actuator/health/**").permitAll().anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }

    @Bean JwtService jwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes:720}") long minutes) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
        return new JwtService(secret, minutes);
    }

    static class JwtService {
        private final Key key; private final long minutes;
        JwtService(String secret, long minutes) { this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.minutes = minutes; }
        String create(long userId, String email) { long now = System.currentTimeMillis(); return Jwts.builder().subject(Long.toString(userId)).claim("email", email).issuedAt(new Date(now)).expiration(new Date(now + minutes * 60_000)).signWith(key).compact(); }
        Claims parse(String token) { return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build().parseSignedClaims(token).getPayload(); }
    }

    @Bean JwtFilter jwtFilter(JwtService service) { return new JwtFilter(service); }
    static class JwtFilter extends OncePerRequestFilter {
        private final JwtService service; JwtFilter(JwtService service) { this.service = service; }
        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            String header = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    Claims claims = service.parse(header.substring(7)); long id = Long.parseLong(claims.getSubject());
                    var auth = new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ex) {
                    res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Invalid or expired access token\"}"); return;
                }
            }
            chain.doFilter(req, res);
        }
    }
}
