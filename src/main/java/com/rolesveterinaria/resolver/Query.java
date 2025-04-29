package com.rolesveterinaria.resolver;

import com.rolesveterinaria.OracleDBConnection;
import com.rolesveterinaria.model.Rol;
import java.sql.*;
import java.util.*;

public class Query {
    public List<Rol> roles() {
        List<Rol> lista = new ArrayList<>();
        try (Connection conn = OracleDBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, nombre, descripcion FROM roles");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Rol r = new Rol();
                r.setId(rs.getInt("id"));
                r.setNombre(rs.getString("nombre"));
                r.setDescripcion(rs.getString("descripcion"));
                lista.add(r);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }
}