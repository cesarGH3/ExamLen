package api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import logica.Usuario;
import modelo.UsuarioDAO;
import servlet.InicializadorAplicacion;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * POST /api/v1/auth/token: intercambia credenciales por un token Bearer de vida corta.
 * Acepta application/json ({"usuario":"...","contrasena":"..."}) o formulario
 * (usuario=...&contrasena=...). Reutiliza los controles de autenticacion de ExamLen y no crea
 * sesion. La emision solo es alcanzable sobre HTTPS por el filtro de cabeceras.
 */
@WebServlet(name = "ApiTokenServlet", urlPatterns = "/api/v1/auth/token")
public class ApiTokenServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MAX_CUERPO = 8192;

    private UsuarioDAO usuarioDAO;
    private modelo.TokenApiDAO tokenDAO;

    @Override
    public void init() throws ServletException {
        usuarioDAO = (UsuarioDAO) getServletContext().getAttribute(InicializadorAplicacion.USUARIO_DAO);
        tokenDAO = (modelo.TokenApiDAO) getServletContext().getAttribute(InicializadorAplicacion.TOKEN_API_DAO);
        if (usuarioDAO == null || tokenDAO == null) {
            throw new ServletException("La aplicacion no se inicializo correctamente.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Credenciales credenciales = leerCredenciales(req);
            if (credenciales == null) {
                ApioJson.error(resp, HttpServletResponse.SC_BAD_REQUEST, "SOLICITUD_INVALIDA",
                        "Indica usuario y contrasena.");
                return;
            }
            AutenticacionApi.Resultado resultado = AutenticacionApi.autenticar(
                    usuarioDAO, credenciales.usuario, credenciales.contrasena);
            ApiTokens.Token token = ApiTokens.emitir(resultado.usuario.consultarIdUsuario(), tokenDAO);

            JsonObject usuario = new JsonObject();
            usuario.addProperty("id", resultado.usuario.consultarIdUsuario());
            usuario.addProperty("nombre", resultado.usuario.nombreUsuario);
            usuario.addProperty("correo", resultado.usuario.correoUsuario);
            usuario.addProperty("rol", resultado.rol);

            JsonObject cuerpo = new JsonObject();
            cuerpo.addProperty("token", token.valor);
            cuerpo.addProperty("tipo", "Bearer");
            cuerpo.addProperty("expiraEn", Math.max(0,
                    token.expiraEnSegundos - System.currentTimeMillis() / 1000L));
            cuerpo.add("usuario", usuario);
            ApioJson.responderCorrecto(resp, cuerpo);
        } catch (AutenticacionApi.Fallo e) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, e.codigo, e.getMessage());
        } catch (CuerpoDemasiadoGrande e) {
            ApioJson.error(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "SOLICITUD_INVALIDA",
                    "La solicitud es demasiado grande.");
        } catch (SolicitudInvalida e) {
            ApioJson.error(resp, HttpServletResponse.SC_BAD_REQUEST, "SOLICITUD_INVALIDA",
                    "No fue posible interpretar la solicitud.");
        } catch (RuntimeException e) {
            getServletContext().log("Fallo interno al emitir token API (" + e.getClass().getSimpleName() + ").");
            ApioJson.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                    "No fue posible emitir el token.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Allow", "POST");
        ApioJson.error(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "Usa POST para obtener un token.");
    }

    private static Credenciales leerCredenciales(HttpServletRequest req)
            throws IOException, CuerpoDemasiadoGrande {
        String tipo = req.getContentType();
        boolean json = tipo != null && tipo.toLowerCase(java.util.Locale.ROOT).contains("application/json");
        if (!json) {
            String usuario = primero(req.getParameter("usuario"), req.getParameter("correo"),
                    req.getParameter("identificador"));
            String contrasena = req.getParameter("contrasena");
            return vacias(usuario, contrasena) ? null : new Credenciales(usuario.strip(), contrasena);
        }

        long longitud = req.getContentLengthLong();
        if (longitud > MAX_CUERPO) {
            throw new CuerpoDemasiadoGrande();
        }
        StringBuilder cuerpo = new StringBuilder();
        try (BufferedReader lector = req.getReader()) {
            char[] bloque = new char[1024];
            int leidos;
            while ((leidos = lector.read(bloque)) != -1) {
                cuerpo.append(bloque, 0, leidos);
                if (cuerpo.length() > MAX_CUERPO) {
                    throw new CuerpoDemasiadoGrande();
                }
            }
        }
        if (cuerpo.toString().isBlank()) {
            return null;
        }
        JsonObject objeto;
        try {
            JsonElement raiz = JsonParser.parseString(cuerpo.toString());
            if (!raiz.isJsonObject()) throw new SolicitudInvalida();
            objeto = raiz.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            throw new SolicitudInvalida();
        }
        String usuario = texto(objeto, "usuario");
        if (usuario == null) {
            usuario = texto(objeto, "correo");
        }
        if (usuario == null) {
            usuario = texto(objeto, "identificador");
        }
        String contrasena = texto(objeto, "contrasena");
        return vacias(usuario, contrasena) ? null : new Credenciales(usuario.strip(), contrasena);
    }

    private static String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || valor.isJsonNull()) return null;
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()) throw new SolicitudInvalida();
        return valor.getAsString();
    }

    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }

    private static boolean vacias(String usuario, String contrasena) {
        return usuario == null || usuario.isBlank() || contrasena == null || contrasena.isEmpty();
    }

    private static final class SolicitudInvalida extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class Credenciales {
        final String usuario;
        final String contrasena;

        Credenciales(String usuario, String contrasena) {
            this.usuario = usuario;
            this.contrasena = contrasena;
        }
    }

    private static final class CuerpoDemasiadoGrande extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
