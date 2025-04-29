package com.rolesveterinaria;

import com.microsoft.azure.functions.annotation.*;
import com.rolesveterinaria.config.GraphQLProvider;
import com.microsoft.azure.functions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.ExecutionResult;
import graphql.GraphQL;

import java.util.Optional;

/**
 * Azure Function - Consulta de roles usando GraphQL
 */
public class ConsultaRolesFunction {

    @FunctionName("ConsultaRoles")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        context.getLogger().info("Procesando solicitud GraphQL para consultar roles.");

        try {
            String body = request.getBody().orElse("");
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = (ObjectNode) mapper.readTree(body);

            String query = json.get("query").asText();
            GraphQL graphQL = GraphQLProvider.buildGraphQL();
            ExecutionResult result = graphQL.execute(query);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(mapper.writeValueAsString(result.toSpecification()))
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("Error en la consulta GraphQL: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}
