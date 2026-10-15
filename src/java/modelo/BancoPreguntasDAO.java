package modelo;

import logica.BancoPreguntas;
import logica.Docente;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class BancoPreguntasDAO extends DaoBase {

    public BancoPreguntasDAO(DataSource dataSource) {
        super(dataSource);
    }

    public BancoPreguntas crearBancoPreguntas(Docente docente, String nombre, String descripcion)
            throws ValidacionException {
        BancoPreguntas banco = new BancoPreguntas();
        banco.nombreBancoPreguntas = nombre;
        banco.descripcionBancoPreguntas = descripcion;
        try {
            int id = insertar("INSERT INTO banco_preguntas (id_docente, nombre, "
                    + "descripcion) VALUES (?, ?, ?)", ps -> {
                ps.setInt(1, docente.getIdUsuario());
                ps.setString(2, nombre);
                ps.setString(3, descripcion);
            });
            banco.setIdBancoPreguntas(id);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ya tienes un banco con ese nombre.");
        }
        return banco;
    }

    public void actualizarBancoPreguntas(BancoPreguntas banco, String nombre, String descripcion)
            throws ValidacionException {
        try {
            actualizar("UPDATE banco_preguntas SET nombre = ?, descripcion = ? "
                    + "WHERE id_banco_preguntas = ?", ps -> {
                ps.setString(1, nombre);
                ps.setString(2, descripcion);
                ps.setInt(3, banco.getIdBancoPreguntas());
            });
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ya tienes un banco con ese nombre.");
        }
        banco.nombreBancoPreguntas = nombre;
        banco.descripcionBancoPreguntas = descripcion;
    }

    public Optional<BancoPreguntas> buscarPorId(int idBanco) {
        return consultarUno("SELECT id_banco_preguntas, id_docente, nombre, descripcion "
                + "FROM banco_preguntas WHERE id_banco_preguntas = ?", ps -> ps.setInt(1, idBanco), this::mapear);
    }

    /** Solo devuelve el banco si pertenece al docente indicado. */
    public Optional<BancoPreguntas> buscarPorIdDeDocente(int idBanco, int idDocente) {
        return consultarUno("SELECT id_banco_preguntas, id_docente, nombre, descripcion "
                + "FROM banco_preguntas WHERE id_banco_preguntas = ? AND id_docente = ?", ps -> {
            ps.setInt(1, idBanco);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    public List<BancoPreguntas> listarPorDocente(int idDocente) {
        return consultarLista("SELECT id_banco_preguntas, id_docente, nombre, "
                + "descripcion FROM banco_preguntas WHERE id_docente = ? "
                + "ORDER BY nombre", ps -> ps.setInt(1, idDocente), this::mapear);
    }

    /**
     * CU-15: cantidad de preguntas por estado de cada banco del docente en UNA consulta
     * (evita N+1). Devuelve un mapa banco → (estado → cantidad).
     */
    public java.util.Map<Integer, java.util.Map<logica.EstadoPregunta, Integer>> contarPorEstadoDeDocente(
            int idDocente) {
        java.util.Map<Integer, java.util.Map<logica.EstadoPregunta, Integer>> conteos = new java.util.HashMap<>();
        consultarLista("SELECT p.id_banco_preguntas, p.estado, COUNT(*) AS cantidad "
                + "FROM pregunta p JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "WHERE b.id_docente = ? GROUP BY p.id_banco_preguntas, p.estado",
                ps -> ps.setInt(1, idDocente), rs -> {
                    int idBanco = rs.getInt("id_banco_preguntas");
                    conteos.computeIfAbsent(idBanco, k -> new java.util.EnumMap<>(logica.EstadoPregunta.class))
                            .put(enumeracion(rs, "estado", logica.EstadoPregunta.class), rs.getInt("cantidad"));
                    return null;
                });
        return conteos;
    }

    /** CU-15: cantidad de preguntas por estado de un banco concreto en UNA consulta. */
    public java.util.Map<logica.EstadoPregunta, Integer> contarPorEstado(int idBanco) {
        java.util.Map<logica.EstadoPregunta, Integer> conteos =
                new java.util.EnumMap<>(logica.EstadoPregunta.class);
        consultarLista("SELECT estado, COUNT(*) AS cantidad FROM pregunta "
                + "WHERE id_banco_preguntas = ? GROUP BY estado", ps -> ps.setInt(1, idBanco), rs -> {
                    conteos.put(enumeracion(rs, "estado", logica.EstadoPregunta.class), rs.getInt("cantidad"));
                    return null;
                });
        return conteos;
    }

    private BancoPreguntas mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        BancoPreguntas banco = new BancoPreguntas();
        banco.setIdBancoPreguntas(rs.getInt("id_banco_preguntas"));
        banco.nombreBancoPreguntas = rs.getString("nombre");
        banco.descripcionBancoPreguntas = rs.getString("descripcion");
        return banco;
    }
}
