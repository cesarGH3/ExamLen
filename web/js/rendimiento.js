// TEMPORAL - HERRAMIENTA ACADEMICA DE RENDIMIENTO
// RETIRAR DESPUES DE LA PRUEBA
(function () {
    'use strict';
    var contenedor = document.getElementById('rendimiento-herramienta');
    if (!contenedor) {
        return;
    }
    var url = contenedor.getAttribute('data-url');
    var csrf = contenedor.getAttribute('data-csrf');
    var botonEjecutar = document.getElementById('btn-rendimiento-ejecutar');
    var botonExcel = document.getElementById('btn-rendimiento-excel');
    var estado = document.getElementById('rendimiento-estado');
    var ejecutando = false;
    var pausaIaMs = 3000;
    var limiteIaMs = 180000;

    function detectarEntorno() {
        var host = window.location.hostname.toLowerCase();
        if (host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '[::1]') {
            return 'local';
        }
        if (/(\.ngrok-free\.dev|\.ngrok-free\.app|\.ngrok\.app|\.ngrok\.io)$/.test(host)) {
            return 'ngrok';
        }
        return null;
    }

    function numero(valor) {
        var n = parseFloat(valor);
        return isNaN(n) ? null : n;
    }

    function redondear(valor) {
        return Math.round(valor * 100) / 100;
    }

    function dos(valor) {
        return (valor < 10 ? '0' : '') + valor;
    }

    function ahora() {
        var d = new Date();
        return d.getFullYear() + '-' + dos(d.getMonth() + 1) + '-' + dos(d.getDate()) + ' '
                + dos(d.getHours()) + ':' + dos(d.getMinutes()) + ':' + dos(d.getSeconds());
    }

    function texto(estadoTexto) {
        estado.textContent = estadoTexto;
    }

    function ejecutar() {
        var entorno = detectarEntorno();
        if (!entorno) {
            window.alert('Entorno desconocido: abre ExamLen por localhost/127.0.0.1 o por una URL de ngrok.');
            return;
        }
        if (ejecutando) {
            return;
        }
        ejecutando = true;
        botonEjecutar.disabled = true;
        texto('Preparando la prueba ' + entorno.toUpperCase() + '...');
        fetch(url + '?accion=catalogo', { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (datos) { return secuencia(entorno, datos.casos); })
            .catch(function (e) { ejecutando = false; botonEjecutar.disabled = false; texto('Error: ' + e); });
    }

    function secuencia(entorno, casos) {
        var resultados = [];
        var indice = 0;
        function paso() {
            if (indice >= casos.length) {
                return guardar(entorno, resultados);
            }
            var cu = casos[indice];
            texto('Ejecutando ' + cu.cu_id + ' (' + (indice + 1) + '/' + casos.length + ')...');
            return preparar(cu).then(function (resultado) {
                resultados.push(resultado);
                indice += 1;
                return paso();
            });
        }
        return paso();
    }

    function preparar(cu) {
        var inicio = performance.now();
        return fetch(url + '?accion=preparar&cu=' + encodeURIComponent(cu.cu_id), { credentials: 'same-origin' })
            .then(function (r) {
                var servidorMs = numero(r.headers.get('X-Server-Ms'));
                if (servidorMs === null) {
                    var timing = r.headers.get('Server-Timing') || '';
                    var coincidencia = /dur=([0-9.]+)/.exec(timing);
                    servidorMs = coincidencia ? numero(coincidencia[1]) : null;
                }
                var tipo = r.headers.get('Content-Type') || '';
                if (tipo.indexOf('application/pdf') >= 0) {
                    return r.arrayBuffer().then(function (bytes) {
                        var fin = performance.now();
                        return resultado(cu.cu_id, fin - inicio, servidorMs, bytes.byteLength > 100,
                                bytes.byteLength + ' bytes (PDF completo)', null);
                    });
                }
                return r.text().then(function (cuerpo) {
                    var fin = performance.now();
                    var datos = {};
                    try {
                        datos = JSON.parse(cuerpo);
                    } catch (e) {
                        datos = {};
                    }
                    if (datos.solicitud_id) {
                        return esperarIA(cu, inicio, servidorMs, datos);
                    }
                    var exito = datos.estado_medicion === 'OK';
                    return resultado(cu.cu_id, fin - inicio, servidorMs, exito,
                            datos.error || datos.observacion || '', null);
                });
            })
            .catch(function (e) {
                var fin = performance.now();
                return resultado(cu.cu_id, fin - inicio, null, false, 'Error de red: ' + e, null);
            });
    }

    function esperarIA(cu, inicio, aceptacionServidor, datos) {
        var aceptacionCliente = performance.now() - inicio;
        var limite = performance.now() + limiteIaMs;
        return new Promise(function (resolver) {
            function cierre(valor, exito) {
                var fin = performance.now();
                return resultado(cu.cu_id, fin - inicio, aceptacionServidor, exito,
                        'IA_REAL; estado final=' + valor + '; aceptacion=' + redondear(aceptacionCliente) + ' ms',
                        { aceptacion: redondear(aceptacionCliente), totalIa: redondear(fin - inicio) });
            }
            function consultar() {
                fetch(url + '?accion=estadoIA&solicitud=' + encodeURIComponent(datos.solicitud_id),
                        { credentials: 'same-origin' })
                    .then(function (r) { return r.json(); })
                    .then(function (e) {
                        var valor = e.estado || 'DESCONOCIDA';
                        if (valor === 'COMPLETADA') {
                            resolver(cierre(valor, true));
                        } else if (valor === 'FALLIDA' || valor === 'CANCELADA') {
                            resolver(cierre(valor, false));
                        } else if (performance.now() >= limite) {
                            resolver(cierre('SIN_RESPUESTA_EN_180S', false));
                        } else {
                            window.setTimeout(consultar, pausaIaMs);
                        }
                    })
                    .catch(function (e) { resolver(cierre('ERROR_' + e, false)); });
            }
            consultar();
        });
    }

    function resultado(cuId, totalMs, servidorMs, exito, observacion, ia) {
        var fila = {
            cu_id: cuId,
            estado_medicion: exito ? 'OK' : 'ERROR',
            tiempo_total_ms: redondear(totalMs),
            tiempo_servidor_ms: servidorMs === null ? null : redondear(servidorMs),
            observacion: observacion || '',
            fecha_hora: ahora()
        };
        if (ia) {
            fila.tiempo_aceptacion_ms = ia.aceptacion;
            fila.tiempo_total_ia_ms = ia.totalIa;
        }
        return fila;
    }

    function guardar(entorno, resultados) {
        return fetch(url + '?accion=guardar&entorno=' + entorno + '&_csrf=' + encodeURIComponent(csrf), {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(resultados)
        }).then(function (r) {
            return r.text().then(function (cuerpo) {
                var datos = {};
                try {
                    datos = JSON.parse(cuerpo);
                } catch (e) {
                    datos = {};
                }
                if (!r.ok) {
                    throw new Error(datos.error || ('HTTP ' + r.status));
                }
                return datos;
            });
        }).then(function (datos) {
            return fetch(url + '?accion=limpiar', { credentials: 'same-origin' }).then(function () {
                return datos;
            });
        }).then(function (datos) {
            var errores = 0;
            for (var i = 0; i < resultados.length; i++) {
                if (resultados[i].estado_medicion === 'ERROR') {
                    errores += 1;
                }
            }
            ejecutando = false;
            botonEjecutar.disabled = false;
            texto('Prueba ' + entorno.toUpperCase() + ' completada: ' + datos.guardados + '/34'
                    + (errores ? ' (' + errores + ' con error)' : '')
                    + '. Puedes descargar el Excel cuando quieras.');
        }).catch(function (e) {
            ejecutando = false;
            botonEjecutar.disabled = false;
            texto('No se pudieron guardar los resultados: ' + e.message);
        });
    }

    function descargarExcel() {
        botonExcel.disabled = true;
        texto('Generando el Excel...');
        fetch(url + '?accion=excel', { credentials: 'same-origin' })
            .then(function (r) {
                if (!r.ok) {
                    return r.text().then(function (cuerpo) {
                        var datos = {};
                        try {
                            datos = JSON.parse(cuerpo);
                        } catch (e) {
                            datos = {};
                        }
                        throw new Error(datos.error || ('HTTP ' + r.status));
                    });
                }
                var nombre = 'ExamLen_Rendimiento_Casos_Uso.xlsx';
                var disposicion = r.headers.get('Content-Disposition') || '';
                var coincidencia = /filename="([^"]+)"/.exec(disposicion);
                if (coincidencia) {
                    nombre = coincidencia[1];
                }
                return r.blob().then(function (blob) {
                    return { blob: blob, nombre: nombre };
                });
            })
            .then(function (archivo) {
                var enlace = document.createElement('a');
                enlace.href = URL.createObjectURL(archivo.blob);
                enlace.download = archivo.nombre;
                document.body.appendChild(enlace);
                enlace.click();
                document.body.removeChild(enlace);
                URL.revokeObjectURL(enlace.href);
                botonExcel.disabled = false;
                texto('Excel descargado: ' + archivo.nombre);
            })
            .catch(function (e) {
                botonExcel.disabled = false;
                window.alert('No se pudo descargar el Excel: ' + e.message);
                texto('No se pudo descargar el Excel: ' + e.message);
            });
    }

    botonEjecutar.addEventListener('click', ejecutar);
    botonExcel.addEventListener('click', descargarExcel);
})();
