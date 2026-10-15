package logica;

/** Cuenta de acceso. Solo el identificador es privado; los DAO usan sus accesores tecnicos. */
public abstract class Usuario {

    private int idUsuario;
    public String identificacionUsuario;
    public String nombreUsuario;
    public String correoUsuario;
    public String institucionUsuario;
    public String contrasenaHashUsuario;
    public boolean activoUsuario;
    /** Marcador generico (cualquier rol): debe cambiar la contrasena en el siguiente ingreso (RF-10). */
    public boolean debeCambiarContrasena;

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }
}
