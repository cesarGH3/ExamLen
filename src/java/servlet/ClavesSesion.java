package servlet;

public final class ClavesSesion {

    /** La sesion solo guarda el id; el usuario se recarga en cada peticion para reflejar desactivaciones. */
    public static final String ID_USUARIO = "examlen.idUsuario";
    public static final String MENSAJES_FLASH = "examlen.flash";

    private ClavesSesion() {
    }
}
