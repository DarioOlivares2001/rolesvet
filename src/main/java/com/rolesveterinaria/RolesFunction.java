package com.rolesveterinaria;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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

            JsonNode eventNode = objectMapper.readTree(event);
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
    
        // 1. Obtener el ID del rol por defecto
        int rolPorDefecto = 0;
        String queryRolDefecto = "SELECT id FROM roles WHERE nombre = 'Usuario' FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement stmt = conn.prepareStatement(queryRolDefecto)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                rolPorDefecto = rs.getInt("id");
                context.getLogger().info("Rol por defecto encontrado con ID: " + rolPorDefecto);
            } else {
                context.getLogger().severe("No se encontró el rol por defecto.");
                return;
            }
        }
    
        // 2. Actualizar usuarios con ese rol al rol por defecto
        String updateUsuarios = "UPDATE usuarios SET rol_id = ? WHERE rol_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateUsuarios)) {
            stmt.setInt(1, rolPorDefecto);
            stmt.setInt(2, id);
            int afectados = stmt.executeUpdate();
            context.getLogger().info("Usuarios actualizados con rol por defecto: " + afectados);
        }
    
        // 3. Eliminar el rol
        String deleteRol = "DELETE FROM roles WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteRol)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            context.getLogger().info("Rol eliminado exitosamente.");
        }
    }
}
