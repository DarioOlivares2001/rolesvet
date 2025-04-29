// GraphQLProvider.java
package com.rolesveterinaria.config;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.rolesveterinaria.resolver.Query;

public class GraphQLProvider {
    private static GraphQL graphQL;

    public static GraphQL buildGraphQL() {
        if (graphQL == null) {
            try {
                InputStream schemaStream = GraphQLProvider.class.getClassLoader()
                    .getResourceAsStream("schema.graphqls");
                TypeDefinitionRegistry typeRegistry = new SchemaParser()
                    .parse(new InputStreamReader(schemaStream, StandardCharsets.UTF_8));

                RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                    .type("Query", builder -> builder.dataFetcher("roles", env -> new Query().roles()))
                    .build();

                GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
                graphQL = GraphQL.newGraphQL(schema).build();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return graphQL;
    }
}
