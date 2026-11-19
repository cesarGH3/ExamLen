package modelo;

import logica.Estudiante;
import logica.Fechas;
import logica.HashContrasena;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.util.List;
import javax.sql.DataSource;

public class EstudianteDAO extends DaoBase {

    private static final int MAX_RESULTADOS = 100;

    private final HashContrasena hashContrasena = new HashContrasena();

    public EstudianteDAO(DataSource dataSource) {
        super(dataSource);
    }

    public Estudiante crearEstudiante(String identificacion, String nombre, String correo, String nivelIngles,
                                      String institucion, String contrasena) throws ValidacionException {
        Estudiante estudiante = new Estudiante();
        estudiante.identificacionUsuario = identificacion;
        estudiante.nombreUsuario = nombre;
        estudiante.correoUsuario = correo;
        estudiante.institucionUsuario = institucion == null ? "" : institucion;
        estudiante.nivelInglesEstudiante = nivelIngles;
        estudiante.contrasenaHashUsuario = hashContrasena.generarHash(contrasena);
        estudiante.activoUsuario = true;
        // El servlet exige la aceptación de la política de datos; aquí se registra su fecha (RNF-13).
        java.util.Date aceptacion = Fechas.ahora();
        estudiante.aceptacionTratamientoDatos = aceptacion;

        try {
            int id = enTransaccion(c -> {
                return insertarUsuarioConRol(c, "ESTUDIANTE", null, null, null, nivelIngles,
                        new java.sql.Timestamp(aceptacion.getTime()),
                        "INSERT INTO usuario (identificacion, nombre, correo, "
                        + "institucion, contrasena_hash, activo) VALUES (?, ?, ?, ?, ?, TRUE)", ps -> {
                    ps.setString(1, identificacion);
                    ps.setString(2, nombre);
                    ps.setString(3, correo);
                    ps.setString(4, estudiante.institucionUsuario);
                    ps.setString(5, estudiante.contrasenaHashUsuario);
                });
            });
            estudiante.establecerIdUsuario(id);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("La identificación o el correo ya están registrados.");
        }
        return estudiante;
    }

    public void actualizarEstudiante(Estudiante estudiante, String nivelIngles) {
        actualizar("UPDATE estudiante SET nivel_ingles = ? WHERE id_usuario = ?", ps -> {
            ps.setString(1, nivelIngles);
            ps.setInt(2, estudiante.consultarIdUsuario());
        });
        estudiante.nivelInglesEstudiante = nivelIngles;
    }

    /**
     * Estudiantes para la pantalla de administración (CU-07), con búsqueda por nombre,
     * identificación o correo. Los comodines del texto se escapan para que % y _ sean
     * literales; vacío devuelve el listado completo, limitado a MAX_RESULTADOS.
     */
    public List<Estudiante> listarEstudiantes(String filtro) {
        String texto = filtro == null ? "" : filtro.strip();
        if (texto.isEmpty()) {
            return consultarLista("SELECT " + Mapeos.USUARIO_COLUMNAS + " FROM " + Mapeos.USUARIO_ORIGEN
                    + " WHERE es.id_usuario IS NOT NULL ORDER BY u.nombre LIMIT " + MAX_RESULTADOS,
                    SIN_PARAMETROS, rs -> (Estudiante) Mapeos.usuario(rs));
        }
        String patron = "%" + escaparComodines(texto) + "%";
        return consultarLista("SELECT " + Mapeos.USUARIO_COLUMNAS + " FROM " + Mapeos.USUARIO_ORIGEN
                + " WHERE es.id_usuario IS NOT NULL AND (u.nombre LIKE ? ESCAPE '!' "
                + "OR u.identificacion LIKE ? ESCAPE '!' OR u.correo LIKE ? ESCAPE '!') "
                + "ORDER BY u.nombre LIMIT " + MAX_RESULTADOS,
                ps -> {
                    ps.setString(1, patron);
                    ps.setString(2, patron);
                    ps.setString(3, patron);
                }, rs -> (Estudiante) Mapeos.usuario(rs));
    }

    private static String escaparComodines(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public void eliminarEstudiante(Estudiante estudiante) {
        actualizar("DELETE FROM usuario WHERE id_usuario = ? AND EXISTS "
                + "(SELECT 1 FROM estudiante es WHERE es.id_usuario = usuario.id_usuario)",
                ps -> ps.setInt(1, estudiante.consultarIdUsuario()));
    }
}
