package com.yasmany.reservas.auth;

import com.yasmany.reservas.comun.seguridad.SeguridadProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final SeguridadProperties props;

    public TokenResponse emitir(String usuario) {
        Instant ahora = Instant.now();
        long segundos = props.jwt().expiracion().toSeconds();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("reserva-salas-api")
                .subject(usuario)
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(segundos))
                .build();
        // La cabecera debe declarar HS256: por defecto el encoder asume RS256
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", segundos);
    }
}
