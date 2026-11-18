package api;

import java.util.Optional;
import logica.Usuario;
import modelo.UsuarioDAO;

/**
 * Verificacion de credenciales compartida por el endpoint de tokens. Reutiliza exactamente la
 * logica de ExamLen: correo o identificacion, BCrypt/legacy con rehash, bloqueo 5/15, cuenta
 * inactiva y rol real. No crea sesiones ni registra credenciales.
 */
public final class AutenticacionApi {

    public static final int MAX_INTENTOS = 5;
    public static final int MINUTOS_BLOQUEO = 15;
    public static final String MENSAJE_GENERICO = "Usuario o contrasena incorrectos.";
    public static final String MENSAJE_DESACTIVADA =
            "La cuenta esta desactivada. Contacte al administrador.";

    private AutenticacionApi() {
    }

    public static final class Resultado {
        public final Usuario usuario;
        public final String rol;

        private Resultado(Usuario usuario, String rol) {
            this.usuario = usuario;
            this.rol = rol;
        }
    }

    public static final class Fallo extends Exception {
        private static final long serialVersionUID = 1L;
        public final String codigo;

        Fallo(String codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }
    }

    public static Resultado autenticar(UsuarioDAO usuarioDAO, String identificador, String contrasena)
            throws Fallo {
        String acceso = identificador == null ? "" : identificador.strip();
        if (acceso.isEmpty() || contrasena == null || contrasena.isEmpty()) {
            throw new Fallo("CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
        }
        Optional<Usuario> hallado = usuarioDAO.buscarPorCorreoOIdentificacion(acceso);
        Usuario usuario = hallado.orElse(null);
        boolean contrasenaCorrecta = usuarioDAO.verificarContrasenaUsuario(usuario, contrasena);
        if (usuario == null) {
            throw new Fallo("CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
        }
        if (usuarioDAO.estaBloqueado(usuario.consultarIdUsuario())) {
            throw new Fallo("CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
        }
        if (!contrasenaCorrecta) {
            usuarioDAO.registrarIntentoFallido(usuario.consultarIdUsuario(), MAX_INTENTOS, MINUTOS_BLOQUEO);
            throw new Fallo("CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
        }
        if (!usuario.activoUsuario) {
            throw new Fallo("CUENTA_DESACTIVADA", MENSAJE_DESACTIVADA);
        }
        usuarioDAO.reiniciarIntentos(usuario.consultarIdUsuario());
        usuarioDAO.recalcularHashSiEsAnterior(usuario, contrasena);

        String rol = usuarioDAO.rolDeUsuario(usuario.consultarIdUsuario()).orElse("");
        if (rol.isBlank()) {
            throw new Fallo("CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
        }
        return new Resultado(usuario, rol);
    }
}
