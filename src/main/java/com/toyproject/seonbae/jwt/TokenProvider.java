package com.toyproject.seonbae.jwt;

import com.toyproject.seonbae.user.domain.model.Authority;
import com.toyproject.seonbae.user.domain.model.Token;
import com.toyproject.seonbae.user.domain.repository.TokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TokenProvider implements InitializingBean {
    private TokenRepository tokenRepository;
    private static final String AUTHORITIES_KEY = "auth";
    private static final String SEQ_NO_KEY = "id";

    private final String secret;
    private final long tokenValidityInMilliseconds;
    private Key key;

    public TokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.token-validity-in-seconds}") long tokenValidityInSeconds,
            TokenRepository tokenRepository) {
        this.secret = secret;
        this.tokenValidityInMilliseconds = tokenValidityInSeconds * 1000;
        this.tokenRepository = tokenRepository;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(com.toyproject.seonbae.user.domain.model.User user) {
        String authorities = user.getAuthorities().stream()
                .map(Authority::getAuthorityName)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date accessTokenValidTime = new Date(now + this.tokenValidityInMilliseconds);

        return Jwts.builder()
                .setSubject(user.getUserName())
                .setIssuedAt(new Date())
                .claim(AUTHORITIES_KEY, authorities)
                .claim(SEQ_NO_KEY, user.getUserId())
                .signWith(key, SignatureAlgorithm.HS512)
                .setExpiration(accessTokenValidTime)
                .compact();
    }

    public String createRefreshToken(com.toyproject.seonbae.user.domain.model.User user) {
        String authorities = user.getAuthorities().stream()
                .map(Authority::getAuthorityName)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date refreshTokenValidTime = new Date(now + this.tokenValidityInMilliseconds * 7);

        String refreshToken = Jwts.builder()
                .setSubject(user.getUserName())
                .setIssuedAt(new Date())
                .claim(AUTHORITIES_KEY, authorities)
                .claim(SEQ_NO_KEY, user.getUserId())
                .signWith(key, SignatureAlgorithm.HS512)
                .setExpiration(refreshTokenValidTime)
                .compact();

        tokenRepository.save(Token.builder().token(refreshToken).build());

        return refreshToken;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts
                .parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        User principal = new User(claims.getSubject(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }
}
