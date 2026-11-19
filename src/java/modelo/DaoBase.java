package modelo;

import logica.AccesoDatosException;
import logica.RegistroDuplicadoException;
import logica.RegistroEnUsoException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Infraestructura común de acceso a datos: conexiones, consultas parametrizadas, transacciones y errores. */
public abstract class DaoBase {

    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_ROW_IS_REFERENCED = 1217;
    private static final int ER_ROW_IS_REFERENCED_2 = 1451;

    @FunctionalInterface
    protected interface Parametros {
        void aplicar(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    protected interface Mapeador<T> {
        T mapear(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    protected interface Transaccion<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }

    protected static final Parametros SIN_PARAMETROS = ps -> { };

    private final DataSource dataSource;

    protected DaoBase(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    /** Conexion directa para operaciones que necesitan controlar commit y bloqueos. */
    protected Connection nuevaConexion() throws SQLException {
        return dataSource.getConnection();
    }

    protected <T> Optional<T> consultarUno(String sql, Parametros parametros, Mapeador<T> mapeador) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros.aplicar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapeador.mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    protected <T> List<T> consultarLista(String sql, Parametros parametros, Mapeador<T> mapeador) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros.aplicar(ps);
            return listar(ps, mapeador);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    protected boolean existe(String sql, Parametros parametros) {
        return consultarUno(sql, parametros, rs -> Boolean.TRUE).isPresent();
    }

    protected int contar(String sql, Parametros parametros) {
        return consultarUno(sql, parametros, rs -> rs.getInt(1)).orElse(0);
    }

    protected int actualizar(String sql, Parametros parametros) {
        try (Connection c = dataSource.getConnection()) {
            return actualizarCon(c, sql, parametros);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /** Inserta y devuelve la clave generada. */
    protected int insertar(String sql, Parametros parametros) {
        try (Connection c = dataSource.getConnection()) {
            return insertarCon(c, sql, parametros);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    protected <T> T enTransaccion(Transaccion<T> transaccion) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommitOriginal = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T resultado = transaccion.ejecutar(c);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    protected static int actualizarCon(Connection c, String sql, Parametros parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            parametros.aplicar(ps);
            return ps.executeUpdate();
        }
    }

    protected static int insertarCon(Connection c, String sql, Parametros parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            parametros.aplicar(ps);
            ps.executeUpdate();
            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("La base de datos no devolvió la clave generada.");
                }
                return claves.getInt(1);
            }
        }
    }

    protected static <T> Optional<T> consultarUnoCon(Connection c, String sql, Parametros parametros,
                                                     Mapeador<T> mapeador) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            parametros.aplicar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(mapeador.mapear(rs)) : Optional.empty();
            }
        }
    }

    protected static <T> List<T> consultarListaCon(Connection c, String sql, Parametros parametros,
                                                   Mapeador<T> mapeador) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            parametros.aplicar(ps);
            return listar(ps, mapeador);
        }
    }

    protected static <E extends Enum<E>> E enumeracion(ResultSet rs, String columna, Class<E> tipo) throws SQLException {
        String valor = rs.getString(columna);
        return valor == null ? null : Enum.valueOf(tipo, valor);
    }

    private static <T> List<T> listar(PreparedStatement ps, Mapeador<T> mapeador) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<T> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapeador.mapear(rs));
            }
            return lista;
        }
    }

    protected static RuntimeException traducir(SQLException e) {
        switch (e.getErrorCode()) {
            case ER_DUP_ENTRY:
                return new RegistroDuplicadoException("Restricción única violada", e);
            case ER_ROW_IS_REFERENCED:
            case ER_ROW_IS_REFERENCED_2:
                return new RegistroEnUsoException("El registro está referenciado por otros datos", e);
            default:
                return new AccesoDatosException("Error de acceso a datos", e);
        }
    }
}
