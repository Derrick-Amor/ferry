package seb.auth;
import cn.hutool.jwt.JWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expire}")
    private long expire;

    private byte[] key() { return secret.getBytes(StandardCharsets.UTF_8); }

    public String createToken(Long userId, Integer role) {
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("role", role)
                .setIssuedAt(new Date())
                .setExpiresAt(new Date(System.currentTimeMillis() + expire * 1000))
                .setKey(key())
                .sign();
    }

    public boolean verify(String token) {
        try {
            JWT jwt = JWT.of(token).setKey(key());
            return jwt.verify() && jwt.validate(0);
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(JWT.of(token).getPayload("userId").toString());
    }

    public Integer getRole(String token) {
        return Integer.parseInt(JWT.of(token).getPayload("role").toString());
    }
}