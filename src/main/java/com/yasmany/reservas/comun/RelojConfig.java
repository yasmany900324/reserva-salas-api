package com.yasmany.reservas.comun;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El "ahora" se inyecta como Clock en vez de llamar a now() directamente,
 * para poder fijarlo en los tests (reglas de "en el pasado" y "ya terminó").
 */
@Configuration
public class RelojConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
