package org.example.restful.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JWTService {

    private static final String SECRET_KEY = "@z4l5#l^+$n0j_d1i^ka+w)o366t(1445x&ph$ysst-8rf%s%o";

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 jour
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
    }

    public boolean verifyCredentials(String username, String password) {
        // Implémentez la vérification des identifiants (via RMI ou base de données)
        return "admin".equals(username) && "password".equals(password);
    }
}
