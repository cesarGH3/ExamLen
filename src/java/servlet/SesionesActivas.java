package servlet;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro en memoria de las sesiones abiertas por usuario (infraestructura web).
 * Permite cerrar todas las sesiones de un usuario cuando restablece su contrasena (RF-11).
 */
@WebListener
public class SesionesActivas implements HttpSessionListener {

    private static final Map<Integer, Set<HttpSession>> POR_USUARIO = new ConcurrentHashMap<>();

    public static void registrar(int idUsuario, HttpSession sesion) {
        POR_USUARIO.computeIfAbsent(idUsuario, clave -> ConcurrentHashMap.newKeySet()).add(sesion);
    }

    /** Invalida todas las sesiones abiertas del usuario. */
    public static void cerrarSesionesDe(int idUsuario) {
        Set<HttpSession> sesiones = POR_USUARIO.remove(idUsuario);
        if (sesiones == null) {
            return;
        }
        for (HttpSession sesion : sesiones) {
            try {
                sesion.invalidate();
            } catch (IllegalStateException yaInvalida) {
                // La sesion ya habia expirado: nada que hacer.
            }
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent evento) {
        POR_USUARIO.values().forEach(sesiones -> sesiones.remove(evento.getSession()));
    }
}
