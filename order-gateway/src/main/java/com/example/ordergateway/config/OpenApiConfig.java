package com.example.ordergateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .addServersItem(new Server().url("/").description("Order Gateway"))
            .info(new Info()
                .title("Order Gateway API")
                .version("1.0")
                .description("Order Gateway - Event-Driven Architecture POC"))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new Components()
                .addSecuritySchemes("Bearer Authentication",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSchemas("OrderRequest", new Schema<>()
                    .type("object")
                    .addProperty("customerId", new Schema<>().type("string").description("Customer ID"))
                    .addProperty("productId", new Schema<>().type("string").description("Product ID"))
                    .addProperty("quantity", new Schema<>().type("integer").minimum(BigDecimal.ONE).description("Quantity"))
                    .addProperty("amount", new Schema<>().type("number").format("decimal").minimum(BigDecimal.valueOf(0.01)).description("Amount"))
                    .required(java.util.List.of("customerId", "productId", "quantity", "amount")))
                .addSchemas("OrderResponse", new Schema<>()
                    .type("object")
                    .addProperty("orderId", new Schema<>().type("integer").format("int64"))
                    .addProperty("customerId", new Schema<>().type("string"))
                    .addProperty("productId", new Schema<>().type("string"))
                    .addProperty("quantity", new Schema<>().type("integer"))
                    .addProperty("amount", new Schema<>().type("number").format("decimal"))
                    .addProperty("status", new Schema<>().type("string"))))
            .path("/api/orders", new PathItem()
                .post(new Operation()
                    .summary("Create new order")
                    .description("Create a new order and publish event to Kafka")
                    .tags(java.util.List.of("Orders"))
                    .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                    .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content()
                            .addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/OrderRequest")))))
                    .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                            .description("Order created successfully")
                            .content(new Content()
                                .addMediaType("application/json", new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/OrderResponse")))))
                        .addApiResponse("400", new ApiResponse().description("Invalid request"))
                        .addApiResponse("401", new ApiResponse().description("Unauthorized"))
                        .addApiResponse("429", new ApiResponse().description("Too many requests")))));
    }
}
