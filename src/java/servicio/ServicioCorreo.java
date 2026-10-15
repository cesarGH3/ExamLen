package servicio;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Envio de correo saliente por SMTP (RF-11). Si falta configuracion o el servidor no responde,
 * registra el motivo en el log y devuelve false: nunca lanza error hacia el usuario.
 */
public class ServicioCorreo {

    private static final Logger LOG = Logger.getLogger(ServicioCorreo.class.getName());
    private static final int TIMEOUT_MS = 10_000;

    public boolean enviarEnlaceRecuperacion(String destinatario, String enlace) {
        if (!ConfiguracionCorreo.estaConfigurado()) {
            LOG.severe(ConfiguracionCorreo.mensajeFaltaConfiguracion());
            return false;
        }
        try {
            Session sesion = Session.getInstance(propiedades(), autenticador());
            MimeMessage mensaje = new MimeMessage(sesion);
            mensaje.setFrom(new InternetAddress(ConfiguracionCorreo.remitente()));
            mensaje.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            mensaje.setSubject("Recuperación de contraseña | ExamLen", StandardCharsets.UTF_8.name());
            mensaje.setText(cuerpo(enlace), StandardCharsets.UTF_8.name());
            Transport.send(mensaje);
            return true;
        } catch (MessagingException e) {
            LOG.log(Level.SEVERE, "No se pudo enviar el correo de recuperación a " + destinatario
                    + ". Revisa la configuración SMTP en " + ConfiguracionIA.ubicacionArchivo(), e);
            return false;
        }
    }

    private Properties propiedades() {
        Properties propiedades = new Properties();
        propiedades.put("mail.smtp.host", ConfiguracionCorreo.host());
        propiedades.put("mail.smtp.port", String.valueOf(ConfiguracionCorreo.puerto()));
        propiedades.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MS));
        propiedades.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MS));
        propiedades.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MS));
        String seguridad = ConfiguracionCorreo.seguridad();
        if ("ssl".equals(seguridad)) {
            propiedades.put("mail.smtp.ssl.enable", "true");
        } else if ("starttls".equals(seguridad)) {
            propiedades.put("mail.smtp.starttls.enable", "true");
            propiedades.put("mail.smtp.starttls.required", "true");
        }
        if (ConfiguracionCorreo.usuario() != null && !ConfiguracionCorreo.usuario().isBlank()) {
            propiedades.put("mail.smtp.auth", "true");
        }
        return propiedades;
    }

    private Authenticator autenticador() {
        String usuario = ConfiguracionCorreo.usuario();
        if (usuario == null || usuario.isBlank()) {
            return null;
        }
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(usuario, ConfiguracionCorreo.clave());
            }
        };
    }

    private String cuerpo(String enlace) {
        return "Hola,\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña de ExamLen.\n"
                + "Abre este enlace para definir una contraseña nueva "
                + "(válido por 30 minutos y un solo uso):\n\n"
                + enlace + "\n\n"
                + "Si no solicitaste este cambio, ignora este mensaje: tu contraseña sigue igual.\n";
    }
}
