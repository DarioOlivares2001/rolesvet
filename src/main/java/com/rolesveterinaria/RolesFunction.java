package com.rolesveterinaria;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Azure Function para consumir eventos de roles desde Event Grid
 */
public class RolesFunction {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("Roles")
    public void run(
        @EventGridTrigger(name = "event") String event,
        final ExecutionContext context) {

        context.getLogger().info("Evento de roles recibido desde Event Grid.");

        if (event == null || event.isEmpty()) {
            context.getLogger().severe("Evento vacío recibido.");
            return;
        }

        try (Connection conn = OracleDBConnection.getConnection()) {
            context.getLogger().info("Conexión a la base de datos establecida correctamente.");

            JsonNode eventNode = objectMapper.readTree(event); // ya no array, directo
            String eventType = eventNode.get("eventType").asText();
            JsonNode rolNode = eventNode.get("data");

            context.getLogger().info("Tipo de evento recibido: " + eventType);

            switch (eventType) {
                case "RolCreado":
                    handleCreateRol(rolNode, conn, context);
                    break;
                case "RolActualizado":
                    handleUpdateRol(rolNode, conn, context);
                    break;
                case "RolEliminado":
                    handleDeleteRol(rolNode, conn, context);
                    break;
                default:
                    context.getLogger().warning("Evento no soportado: " + eventType);
                    break;
            }
        } catch (Exception e) {
            context.getLogger().severe("Error procesando evento: " + e.getMessage());
        }
    }

    private void handleCreateRol(JsonNode rolNode, Connection conn, ExecutionContext context) throws SQLException {
        if (rolNode == null || rolNode.isEmpty()) {
            context.getLogger().severe("Datos de rol vacíos en creación.");
            return;
        }

        String nombre = rolNode.get("nombre").asText("");
        String descripcion = rolNode.get("descripcion").asText("");

        if (nombre.isEmpty() || descripcion.isEmpty()) {
            context.getLogger().severe("Faltan campos requeridos para crear rol.");
            return;
        }

        String query = "INSERT INTO roles (nombre, descripcion) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nombre);
            stmt.setString(2, descripcion);
            stmt.executeUpdate();
            context.getLogger().info("Rol creado exitosamente.");
        }
    }

    private void handleUpdateRol(JsonNode rolNode, Connection conn, ExecutionContext context) throws SQLException {
        int id = rolNode.has("id") ? rolNode.get("id").asInt() : 0;
        String nombre = rolNode.get("nombre").asText("");
        String descripcion = rolNode.get("descripcion").asText("");

        if (id == 0 || nombre.isEmpty() || descripcion.isEmpty()) {
            context.getLogger().severe("Faltan campos requeridos para actualizar rol.");
            return;
        }

        String query = "UPDATE roles SET nombre = ?, descripcion = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, nombre);
            stmt.setString(2, descripcion);
            stmt.setInt(3, id);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                context.getLogger().warning("Rol no encontrado para actualizar.");
            } else {
                context.getLogger().info("Rol actualizado exitosamente.");
            }
        }
    }

    private void handleDeleteRol(JsonNode rolNode, Connection conn, ExecutionContext context) throws SQLException {
        int id = rolNode.has("id") ? rolNode.get("id").asInt() : 0;

        if (id == 0) {
            context.getLogger().severe("Falta el campo 'id' para eliminar rol.");
            return;
        }

        String query = "DELETE FROM roles WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            int deleted = stmt.executeUpdate();
            if (deleted == 0) {
                context.getLogger().warning("Rol no encontrado para eliminar.");
            } else {
                context.getLogger().info("Rol eliminado exitosamente.");
            }
        }
    }
}
