package com.yasmany.reservas.comun.openapi;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

/**
 * Documentación OpenAPI. Swagger UI en /swagger-ui.html y el JSON en /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String LOGIN = "/auth/login";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de reserva de salas")
                        .version("0.0.1")
                        .description("""
                                Las consultas (GET) son públicas. Para crear, modificar o cancelar: \
                                obtén un token con POST /auth/login y pulsa **Authorize**."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * Completa cada operación con lo que se deduce de las reglas generales de la API,
     * para no repetir anotaciones en cada método:
     * <ul>
     *   <li>401 y candado en todo lo que no es GET, salvo el login (misma regla que SecurityConfig).</li>
     *   <li>400 si la operación recibe cuerpo o parámetros.</li>
     *   <li>404 si la ruta lleva un id.</li>
     * </ul>
     * Todos los errores usan el esquema ProblemDetail (RFC 9457).
     */
    @Bean
    OpenApiCustomizer respuestasComunes() {
        return openApi -> {
            ModelConverters.getInstance().readAll(ProblemDetail.class)
                    .forEach(openApi.getComponents()::addSchemas);
            openApi.getPaths().forEach((ruta, item) ->
                    item.readOperationsMap().forEach((metodo, operacion) -> {
                        boolean protegida = metodo != PathItem.HttpMethod.GET && !LOGIN.equals(ruta);
                        if (protegida) {
                            operacion.addSecurityItem(new SecurityRequirement().addList(BEARER));
                            error(operacion, "401", "Falta el token o no es válido");
                        }
                        if (operacion.getRequestBody() != null
                                || (operacion.getParameters() != null && !operacion.getParameters().isEmpty())) {
                            error(operacion, "400", "Datos o parámetros inválidos");
                        }
                        if (ruta.contains("{")) {
                            error(operacion, "404", "El recurso no existe");
                        }
                    }));
        };
    }

    private static void error(Operation operacion, String codigo, String descripcion) {
        if (operacion.getResponses().containsKey(codigo)) {
            return; // respeta lo documentado a mano en el controlador
        }
        operacion.getResponses().addApiResponse(codigo, new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")))));
    }
}
