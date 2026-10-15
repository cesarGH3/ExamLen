package org.apache.commons.logging;

/**
 * Implementación mínima de Commons Logging delegando en java.util.logging.
 * PDFBox/fontbox la necesitan en tiempo de ejecución y el proyecto no incluye el jar externo;
 * esta clase cubre únicamente el contrato que usan (LogFactory.getLog y Log).
 */
public interface Log {

    void debug(Object message);

    void debug(Object message, Throwable t);

    void error(Object message);

    void error(Object message, Throwable t);

    void fatal(Object message);

    void fatal(Object message, Throwable t);

    void info(Object message);

    void info(Object message, Throwable t);

    boolean isDebugEnabled();

    boolean isErrorEnabled();

    boolean isFatalEnabled();

    boolean isInfoEnabled();

    boolean isTraceEnabled();

    boolean isWarnEnabled();

    void trace(Object message);

    void trace(Object message, Throwable t);

    void warn(Object message);

    void warn(Object message, Throwable t);
}
