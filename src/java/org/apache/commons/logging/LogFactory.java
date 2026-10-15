package org.apache.commons.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fábrica mínima de Commons Logging (shim propio, sin jar externo): PDFBox la requiere para
 * cargar PDDocument. Delega en java.util.logging y no guarda estado estático del webapp.
 */
public abstract class LogFactory {

    private static final LogFactory INSTANCIA = new LogFactoryJUL();

    public static Log getLog(Class<?> tipo) {
        return INSTANCIA.getInstance(tipo.getName());
    }

    public static Log getLog(String nombre) {
        return INSTANCIA.getInstance(nombre);
    }

    public static LogFactory getFactory() {
        return INSTANCIA;
    }

    public static void release(ClassLoader cargador) {
        // Sin estado que liberar.
    }

    public abstract Log getInstance(String nombre);

    public Object getAttribute(String nombre) {
        return null;
    }

    public String[] getAttributeNames() {
        return new String[0];
    }

    public void setAttribute(String nombre, Object valor) {
        // No soportado: el shim no configura nada.
    }

    public void release() {
        // Sin estado que liberar.
    }

    /** Implementación sobre java.util.logging. */
    private static final class LogFactoryJUL extends LogFactory {

        @Override
        public Log getInstance(String nombre) {
            return new LogJUL(Logger.getLogger(nombre));
        }
    }

    private static final class LogJUL implements Log {

        private final Logger registro;

        LogJUL(Logger registro) {
            this.registro = registro;
        }

        @Override
        public void debug(Object message) {
            registro.log(Level.FINE, String.valueOf(message));
        }

        @Override
        public void debug(Object message, Throwable t) {
            registro.log(Level.FINE, String.valueOf(message), t);
        }

        @Override
        public void error(Object message) {
            registro.log(Level.SEVERE, String.valueOf(message));
        }

        @Override
        public void error(Object message, Throwable t) {
            registro.log(Level.SEVERE, String.valueOf(message), t);
        }

        @Override
        public void fatal(Object message) {
            registro.log(Level.SEVERE, String.valueOf(message));
        }

        @Override
        public void fatal(Object message, Throwable t) {
            registro.log(Level.SEVERE, String.valueOf(message), t);
        }

        @Override
        public void info(Object message) {
            registro.log(Level.INFO, String.valueOf(message));
        }

        @Override
        public void info(Object message, Throwable t) {
            registro.log(Level.INFO, String.valueOf(message), t);
        }

        @Override
        public boolean isDebugEnabled() {
            return registro.isLoggable(Level.FINE);
        }

        @Override
        public boolean isErrorEnabled() {
            return registro.isLoggable(Level.SEVERE);
        }

        @Override
        public boolean isFatalEnabled() {
            return registro.isLoggable(Level.SEVERE);
        }

        @Override
        public boolean isInfoEnabled() {
            return registro.isLoggable(Level.INFO);
        }

        @Override
        public boolean isTraceEnabled() {
            return registro.isLoggable(Level.FINER);
        }

        @Override
        public boolean isWarnEnabled() {
            return registro.isLoggable(Level.WARNING);
        }

        @Override
        public void trace(Object message) {
            registro.log(Level.FINER, String.valueOf(message));
        }

        @Override
        public void trace(Object message, Throwable t) {
            registro.log(Level.FINER, String.valueOf(message), t);
        }

        @Override
        public void warn(Object message) {
            registro.log(Level.WARNING, String.valueOf(message));
        }

        @Override
        public void warn(Object message, Throwable t) {
            registro.log(Level.WARNING, String.valueOf(message), t);
        }
    }
}
