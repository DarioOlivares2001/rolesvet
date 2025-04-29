package com.rolesveterinaria;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class RolesFunction {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("Roles")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Procesando solicitud de roles.");

        // Verificar la conexión a la base de datos antes de continuar
        if (!OracleDBConnection.testConnection()) {
            context.getLogger().severe("Error: No se pudo conectar a la base de datos.");
            return createErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo conectar a la base de datos");
        }

        String method = request.getHttpMethod().toString();

        try (Connection conn = OracleDBConnection.getConnection()) {
            context.getLogger().info("Conexión a la base de datos establecida correctamente.");

            switch (method) {
                case "GET":
                    return handleGetRoles(request, conn, context);
                case "POST":
                    return handleCreateRole(request, conn, context);
                case "PUT":
                    return handleUpdateRole(request, conn, context);
                case "DELETE":
                    return handleDeleteRole(request, conn, context);
                default:
                    return createErrorResponse(request, HttpStatus.METHOD_NOT_ALLOWED, "Método no soportado");
            }
        } catch (SQLException e) {
            context.getLogger().severe("Error en la conexión a Oracle: " + e.getMessage());
            return createErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Error de conexión a la base de datos: " + e.getMessage());
        } catch (Exception e) {
            context.getLogger().severe("Error al procesar la solicitud: " + e.getMessage());
            return createErrorResponse(request, HttpStatus.BAD_REQUEST, "Error al procesar la solicitud: " + e.getMessage());
        }
    }

    private HttpResponseMessage createErrorResponse(HttpRequestMessage<Optional<String>> request, HttpStatus status, String message) {
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("error", message);
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(errorResponse.toString())
                .build();
    }

    private HttpResponseMessage handleGetRoles(HttpRequestMessage<Optional<String>> request, Connection conn, ExecutionContext context) throws Exception {
        String query = "SELECT id, nombre, descripcion FROM roles";
        ObjectNode responseBody = objectMapper.createObjectNode();
        
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            ObjectNode rolesArray = objectMapper.createObjectNode();
            int index = 0;
            while (rs.next()) {
                ObjectNode role = objectMapper.createObjectNode();
                role.put("id", rs.getInt("id"));
                role.put("nombre", rs.getString("nombre"));
                role.put("descripcion", rs.getString("descripcion"));
                rolesArray.set(String.valueOf(index++), role);
            }
            responseBody.set("roles", rolesArray);
        } catch (SQLException e) {
            context.getLogger().severe("Error al ejecutar la consulta de roles: " + e.getMessage());
            throw e;
        }

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(responseBody.toString())
                .build();
    }

    private HttpResponseMessage handleCreateRole(HttpRequestMessage<Optional<String>> request, Connection conn, ExecutionContext context) throws Exception {
        String body = request.getBody().orElse("{}");
        ObjectNode jsonBody = (ObjectNode) objectMapper.readTree(body);

        // Validar campos requeridos
        if (!jsonBody.has("nombre") || !jsonBody.has("descripcion")) {
            return createErrorResponse(request, HttpStatus.BAD_REQUEST, "Campos 'nombre' y 'descripcion' son requeridos");
        }

        String nombre = jsonBody.get("nombre").asText();
        String descripcion = jsonBody.get("descripcion").asText();

        String query = "INSERT INTO roles (nombre, descripcion) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nombre);
            stmt.setString(2, descripcion);
            stmt.executeUpdate();
        } catch (SQLException e) {
            context.getLogger().severe("Error al insertar el rol: " + e.getMessage());
            throw e;
        }

        ObjectNode successResponse = objectMapper.createObjectNode();
        successResponse.put("message", "Rol creado exitosamente");
        return request.createResponseBuilder(HttpStatus.CREATED)
                .header("Content-Type", "application/json")
                .body(successResponse.toString())
                .build();
    }

    private HttpResponseMessage handleUpdateRole(HttpRequestMessage<Optional<String>> request, Connection conn, ExecutionContext context) throws Exception {
        String body = request.getBody().orElse("{}");
        ObjectNode jsonBody = (ObjectNode) objectMapper.readTree(body);

        // Validar campos requeridos
        if (!jsonBody.has("id") || !jsonBody.has("nombre") || !jsonBody.has("descripcion")) {
            return createErrorResponse(request, HttpStatus.BAD_REQUEST, "Campos 'id', 'nombre' y 'descripcion' son requeridos");
        }

        int id = jsonBody.get("id").asInt();
        String nombre = jsonBody.get("nombre").asText();
        String descripcion = jsonBody.get("descripcion").asText();

        String query = "UPDATE roles SET nombre = ?, descripcion = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nombre);
            stmt.setString(2, descripcion);
            stmt.setInt(3, id);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                return createErrorResponse(request, HttpStatus.NOT_FOUND, "Rol no encontrado");
            }
        } catch (SQLException e) {
            context.getLogger().severe("Error al actualizar el rol: " + e.getMessage());
            throw e;
        }

        ObjectNode successResponse = objectMapper.createObjectNode();
        successResponse.put("message", "Rol actualizado exitosamente");
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(successResponse.toString())
                .build();
    }

    private HttpResponseMessage handleDeleteRole(HttpRequestMessage<Optional<String>> request, Connection conn, ExecutionContext context) throws SQLException {
        String idStr = request.getQueryParameters().get("id");
        if (idStr == null || idStr.isEmpty()) {
            return createErrorResponse(request, HttpStatus.BAD_REQUEST, "El parámetro 'id' es requerido");
        }

        String query = "DELETE FROM roles WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(idStr));
            int deleted = stmt.executeUpdate();
            if (deleted == 0) {
                return createErrorResponse(request, HttpStatus.NOT_FOUND, "Rol no encontrado");
            }
        } catch (SQLException e) {
            context.getLogger().severe("Error al eliminar el rol: " + e.getMessage());
            throw e;
        }

        ObjectNode successResponse = objectMapper.createObjectNode();
        successResponse.put("message", "Rol eliminado exitosamente");
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(successResponse.toString())
                .build();
    }
}