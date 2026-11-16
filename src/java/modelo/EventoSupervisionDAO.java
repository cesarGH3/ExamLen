package modelo;

import logica.Fechas;

import logica.EventoSupervision;
import logica.Intento;
import logica.TipoEventoSupervision;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

public class EventoSupervisionDAO extends DaoBase {

    public EventoSupervisionDAO(DataSource dataSource) {
        super(dataSource);
    }

    public EventoSupervision registrarEventoSupervision(Intento intento, TipoEventoSupervision tipo,
                                                        String descripcion) {
        EventoSupervision evento = new EventoSupervision();
        evento.tipoEventoSupervision = tipo;
        evento.descripcionEventoSupervision = descripcion;
        evento.fechaHoraEventoSupervision = Fechas.ahora();
        int id = insertar("INSERT INTO evento_supervision (id_intento, tipo, "
                + "descripcion) VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, intento.consultarIdIntento());
            ps.setString(2, tipo.name());
            ps.setString(3, descripcion);
        });
        evento.establecerIdEventoSupervision(id);
        intento.eventosSupervisionIntento.add(evento);
        return evento;
    }

    /**
     * Estrategia de deduplicación de RF-49 sin esquema adicional: si el navegador reintenta el
     * mismo evento (misma descripción y tipo) en una ventana corta, no se registra dos veces.
     */
    public boolean existeSimilarReciente(int idIntento, TipoEventoSupervision tipo, String descripcion) {
        return existe("SELECT 1 FROM evento_supervision WHERE id_intento = ? AND tipo = ? "
                + "AND descripcion = ? AND fecha_hora >= NOW() - INTERVAL 3 SECOND LIMIT 1", ps -> {
            ps.setInt(1, idIntento);
            ps.setString(2, tipo.name());
            ps.setString(3, descripcion);
        });
    }

    public int contarPorIntento(int idIntento) {
        return contar("SELECT COUNT(*) FROM evento_supervision WHERE id_intento = ?",
                ps -> ps.setInt(1, idIntento));
    }

    public List<EventoSupervision> listarPorIntento(int idIntento) {
        return consultarLista("SELECT id_evento_supervision, tipo, "
                + "descripcion, fecha_hora FROM evento_supervision "
                + "WHERE id_intento = ? ORDER BY fecha_hora", ps -> ps.setInt(1, idIntento),
                this::mapear);
    }

    /** Indicadores de un intento agrupados por tipo, de mayor a menor (RF-55). */
    public static final class ResumenTipo {
        public TipoEventoSupervision tipo;
        public int total;
    }

    public List<ResumenTipo> contarPorTipo(int idIntento) {
        return consultarLista("SELECT tipo, COUNT(*) AS total FROM evento_supervision "
                + "WHERE id_intento = ? GROUP BY tipo ORDER BY COUNT(*) DESC, tipo",
                ps -> ps.setInt(1, idIntento), rs -> {
                    ResumenTipo fila = new ResumenTipo();
                    fila.tipo = enumeracion(rs, "tipo", TipoEventoSupervision.class);
                    fila.total = rs.getInt("total");
                    return fila;
                });
    }

    /** ¿La evaluación virtual ya tiene eventos de ese tipo? (idempotencia del análisis, RF-50). */
    public boolean existeEventoDeTipoEnVirtual(int idEvaluacionVirtual, TipoEventoSupervision tipo) {
        return existe("SELECT 1 FROM evento_supervision e "
                + "JOIN intento i ON i.id_intento = e.id_intento "
                + "WHERE i.id_evaluacion_virtual = ? AND e.tipo = ? LIMIT 1", ps -> {
            ps.setInt(1, idEvaluacionVirtual);
            ps.setString(2, tipo.name());
        });
    }

    private EventoSupervision mapear(ResultSet rs) throws SQLException {
        EventoSupervision evento = new EventoSupervision();
        evento.establecerIdEventoSupervision(rs.getInt("id_evento_supervision"));
        evento.tipoEventoSupervision = enumeracion(rs, "tipo", TipoEventoSupervision.class);
        evento.descripcionEventoSupervision = rs.getString("descripcion");
        evento.fechaHoraEventoSupervision = Mapeos.fecha(rs, "fecha_hora");
        return evento;
    }
}
