package logica;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Herramienta de consola para crear el hash del primer administrador.
 * No es un servlet: no queda expuesta en la web. Ejecutar con Run File (Shift + F6).
 */
public final class GenerarHashContrasena {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        System.out.print("Contraseña del administrador: ");
        String contrasena = scanner.nextLine();
        if (!PoliticaContrasena.cumple(contrasena)) {
            System.err.println(PoliticaContrasena.DESCRIPCION);
            return;
        }
        System.out.println();
        System.out.println("Hash para sql/crear_administrador.sql:");
        System.out.println(new HashContrasena().generarHash(contrasena));
    }
}
