(function () {
    'use strict';
    var form = document.getElementById('form-examen');
    if (!form) {
        return;
    }
    var csrf = form.getAttribute('data-csrf');
    var urlGuardar = form.getAttribute('data-url-guardar');
    var urlEvento = form.getAttribute('data-url-evento');
    var idIntento = form.querySelector('input[name="id"]').value;
    var supervision = form.getAttribute('data-supervision') === '1';

    // ---- Cuenta regresiva (no pausable; el servidor es la autoridad del tiempo)
    var temporizador = document.getElementById('temporizador');
    var segundos = parseInt(temporizador.getAttribute('data-segundos'), 10);
    if (isNaN(segundos)) {
        segundos = 0;
    }
    function pintar() {
        var minutos = Math.floor(segundos / 60);
        var resto = segundos % 60;
        temporizador.textContent = (minutos < 10 ? '0' : '') + minutos + ':' + (resto < 10 ? '0' : '') + resto;
    }
    pintar();
    var intervalo = setInterval(function () {
        segundos -= 1;
        if (segundos <= 0) {
            clearInterval(intervalo);
            temporizador.textContent = '00:00';
            form.submit();
            return;
        }
        pintar();
    }, 1000);

    // ---- Autoguardado con cola offline de respuestas (CU-27 5a / RNF-05)
    var bloqueado = false;
    var CLAVE_RESPUESTAS = 'examlen.respuestas.' + idIntento;
    var estadoGuardado = document.getElementById('estado-guardado');

    // El servidor es la autoridad del tiempo: si rechaza un guardado (409), la página deja de
    // admitir cambios y lo avisa; el intento ya quedó cerrado en el servidor.
    function bloquearFormulario() {
        if (bloqueado) {
            return;
        }
        bloqueado = true;
        var elementos = form.querySelectorAll('input, textarea, button, select');
        for (var i = 0; i < elementos.length; i++) {
            elementos[i].disabled = true;
        }
        var aviso = document.getElementById('aviso-tiempo');
        if (aviso) {
            aviso.hidden = false;
        }
    }

    function pintarEstado(texto) {
        if (estadoGuardado) {
            estadoGuardado.textContent = texto || '';
        }
    }

    function leerRespuestas() {
        try {
            return JSON.parse(localStorage.getItem(CLAVE_RESPUESTAS) || '[]');
        } catch (e) {
            return [];
        }
    }

    function escribirRespuestas(cola) {
        try {
            localStorage.setItem(CLAVE_RESPUESTAS, JSON.stringify(cola));
        } catch (e) {
            // Sin almacenamiento local: la respuesta se seguirá intentando en memoria.
        }
    }

    /** La respuesta más reciente de una pregunta reemplaza a la pendiente anterior. */
    function encolarRespuesta(datos) {
        var cola = leerRespuestas();
        var siguiente = [];
        for (var i = 0; i < cola.length; i++) {
            if (cola[i].idVersionPregunta !== datos.idVersionPregunta) {
                siguiente.push(cola[i]);
            }
        }
        siguiente.push(normalizar(datos));
        escribirRespuestas(siguiente);
    }

    /** Quita la pendiente solo si sigue siendo exactamente la que el servidor confirmó. */
    function desencolarConfirmada(datos) {
        var cola = leerRespuestas();
        var siguiente = [];
        for (var i = 0; i < cola.length; i++) {
            if (!mismaRespuesta(cola[i], datos)) {
                siguiente.push(cola[i]);
            }
        }
        escribirRespuestas(siguiente);
    }

    function normalizar(datos) {
        return {
            idVersionPregunta: String(datos.idVersionPregunta),
            idOpcion: datos.idOpcion === undefined ? null : String(datos.idOpcion),
            contenido: datos.contenido === undefined ? null : String(datos.contenido)
        };
    }

    function mismaRespuesta(a, b) {
        var na = normalizar(a);
        var nb = normalizar(b);
        return na.idVersionPregunta === nb.idVersionPregunta
                && na.idOpcion === nb.idOpcion
                && na.contenido === nb.contenido;
    }

    function actualizarEstado() {
        if (bloqueado) {
            return;
        }
        if (navigator.onLine === false) {
            pintarEstado('Sin conexión. La respuesta se enviará al reconectarse.');
            return;
        }
        var pendientes = leerRespuestas().length;
        pintarEstado(pendientes > 0 ? 'Pendiente de guardar (' + pendientes + ')' : 'Guardado');
    }

    function enviarRespuesta(datos) {
        var cuerpo = new URLSearchParams();
        cuerpo.append('_csrf', csrf);
        cuerpo.append('id', idIntento);
        cuerpo.append('accion', 'responder');
        cuerpo.append('idVersionPregunta', datos.idVersionPregunta);
        if (datos.idOpcion !== null && datos.idOpcion !== undefined) {
            cuerpo.append('idOpcion', datos.idOpcion);
        }
        if (datos.contenido !== null && datos.contenido !== undefined) {
            cuerpo.append('contenido', datos.contenido);
        }
        return fetch(urlGuardar, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: cuerpo.toString(),
            keepalive: true
        }).then(function (respuesta) {
            if (respuesta.status === 409) {
                return 'vencido';
            }
            return respuesta.status >= 200 && respuesta.status < 300 ? 'ok' : 'fallo';
        }).catch(function () {
            return 'fallo';
        });
    }

    function resolverEnvio(datos, resultado) {
        if (resultado === 'ok') {
            desencolarConfirmada(datos);
            actualizarEstado();
            return;
        }
        if (resultado === 'vencido') {
            escribirRespuestas([]);
            bloqueado = false;
            bloquearFormulario();
            return;
        }
        encolarRespuesta(datos);
        actualizarEstado();
    }

    function guardar(datos) {
        if (bloqueado) {
            return Promise.resolve();
        }
        return enviarRespuesta(normalizar(datos)).then(function (resultado) {
            resolverEnvio(datos, resultado);
        });
    }

    /** Reenvía los pendientes al reconectar; solo se elimina lo confirmado con 2xx. */
    function descargarRespuestas() {
        if (navigator.onLine === false) {
            return Promise.resolve(false);
        }
        var pendientes = leerRespuestas();
        if (pendientes.length === 0) {
            return Promise.resolve(false);
        }
        var primera = pendientes[0];
        return enviarRespuesta(primera).then(function (resultado) {
            if (resultado === 'ok') {
                desencolarConfirmada(primera);
                actualizarEstado();
                return descargarRespuestas();
            }
            if (resultado === 'vencido') {
                escribirRespuestas([]);
                bloquearFormulario();
                return false;
            }
            return false;
        });
    }

    // Antes de un envio manual se intenta vaciar la cola local; el servidor sigue siendo la autoridad.
    var enviandoConCola = false;
    form.addEventListener('submit', function (evento) {
        if (enviandoConCola || bloqueado || leerRespuestas().length === 0) {
            return;
        }
        evento.preventDefault();
        enviandoConCola = true;
        descargarRespuestas().then(function () {
            enviandoConCola = false;
            if (!bloqueado) {
                form.submit();
            }
        });
    });

    form.addEventListener('change', function (evento) {
        var elemento = evento.target;
        if (elemento.type === 'radio') {
            guardar({ idVersionPregunta: elemento.name.replace('opcion_', ''), idOpcion: elemento.value });
        }
    });

    var pendientesTexto = {};
    form.addEventListener('input', function (evento) {
        var elemento = evento.target;
        if (elemento.tagName === 'TEXTAREA') {
            var id = elemento.name.replace('contenido_', '');
            clearTimeout(pendientesTexto[id]);
            pendientesTexto[id] = setTimeout(function () {
                guardar({ idVersionPregunta: id, contenido: elemento.value });
            }, 1200);
        }
    });

    window.addEventListener('offline', function () {
        actualizarEstado();
    });
    window.addEventListener('online', function () {
        descargarRespuestas().then(actualizarEstado);
    });
    // Al cargar la página se reenvían los pendientes de una sesión anterior.
    if (leerRespuestas().length > 0) {
        actualizarEstado();
        descargarRespuestas().then(actualizarEstado);
    }

    // ---- Supervisión (con cola offline en localStorage: RF-49 / CU-28 2a)
    if (supervision) {
        var CLAVE_COLA = 'examlen.eventos.' + idIntento;

        function leerCola() {
            try {
                return JSON.parse(localStorage.getItem(CLAVE_COLA) || '[]');
            } catch (e) {
                return [];
            }
        }

        function guardarCola(cola) {
            try {
                localStorage.setItem(CLAVE_COLA, JSON.stringify(cola));
            } catch (e) {
                // Sin almacenamiento disponible: el evento se intenta enviar igualmente.
            }
        }

        function enviarEvento(datos) {
            var cuerpo = new URLSearchParams();
            cuerpo.append('_csrf', csrf);
            cuerpo.append('idIntento', idIntento);
            cuerpo.append('tipo', datos.tipo);
            cuerpo.append('descripcion', datos.descripcion || '');
            return fetch(urlEvento, { method: 'POST', body: cuerpo, keepalive: true })
                    .then(function (respuesta) {
                        return respuesta.status === 204 || respuesta.status === 200;
                    })
                    .catch(function () {
                        return false;
                    });
        }

        // Reenvía la cola pendiente; solo se elimina localmente lo confirmado por el servidor.
        function descargarCola() {
            if (navigator.onLine === false) {
                return Promise.resolve(false);
            }
            var pendientes = leerCola();
            if (pendientes.length === 0) {
                return Promise.resolve(false);
            }
            return enviarEvento(pendientes[0]).then(function (confirmado) {
                if (!confirmado) {
                    return false;
                }
                var cola = leerCola();
                cola.shift();
                guardarCola(cola);
                return descargarCola().then(function () {
                    return true;
                });
            });
        }

        function evento(tipo, descripcion) {
            var cola = leerCola();
            cola.push({ tipo: tipo, descripcion: descripcion || '' });
            guardarCola(cola);
            descargarCola();
        }

        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                evento('CAMBIO_PESTANA', 'El estudiante salió de la pestaña');
            }
        });
        window.addEventListener('blur', function () {
            evento('PERDIDA_FOCO', 'La ventana perdió el foco');
        });
        window.addEventListener('beforeunload', function () {
            evento('SALIDA_PAGINA', 'El estudiante salió de la página');
        });
        window.addEventListener('offline', function () {
            evento('DESCONEXION', 'Se perdió la conexión');
        });
        window.addEventListener('online', function () {
            descargarCola().then(function () {
                evento('RECONEXION', 'Se restableció la conexión');
            });
        });
        // Al cargar la página también se reenvían los pendientes de una sesión anterior.
        if (leerCola().length > 0) {
            descargarCola();
        }
    }
})();
