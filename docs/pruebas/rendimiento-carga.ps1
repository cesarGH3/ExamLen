<#
.SYNOPSIS
  Prueba de carga de ExamLen (RNF-07 y RNF-08).

.DESCRIPTION
  Simula $Usuarios estudiantes concurrentes presentando un examen real:
    · mide los guardados automáticos de respuesta (RNF-07: 95 % < 2 s),
    · mide las páginas de consulta (RNF-08: < 3 s) y
    · mide la generación del PDF de una versión de 50 preguntas (RNF-08: < 10 s).

  Crea datos temporales identificados con el prefijo "carga-rendimiento-" y los borra
  SIEMPRE al terminar, incluso si falla a mitad. Se niega a correr si detecta restos de
  una corrida anterior (para no mezclar cuentas de prueba con datos reales). Solo elimina
  registros con ese prefijo.

  Requiere: Tomcat en HTTP local (secure=false, ver README), MySQL de desarrollo
  (credenciales leídas de web/META-INF/context.xml) y PowerShell 5.1.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File docs\pruebas\rendimiento-carga.ps1
#>
param(
    [string]$BaseUrl = "http://localhost:8080/ExamLen",
    [int]$Usuarios = 40,
    [int]$GuardadosPorUsuario = 10,
    [int]$Preguntas = 50,
    [string]$MySqlExe = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
    [string]$Prefijo = "carga-rendimiento-"
)

$ErrorActionPreference = "Stop"
# --- Ajustes SOLO del cliente de prueba (diagnóstico de la corrida 1: ver docs/INFORME-RENDIMIENTO.md) ---
# 1) La barra de progreso de Invoke-WebRequest escribe en el host compartido y serializa los
#    runspaces: con 40 concurrentes medía ~2 s por petición trivial. Silenciarla: ~7 ms.
$ProgressPreference = "SilentlyContinue"
# 2) El cliente .NET limita por defecto a 2 las conexiones simultáneas por host.
[System.Net.ServicePointManager]::DefaultConnectionLimit = 100
# 3) El pool de hilos de .NET crece despacio; con 40 runspaces bloqueados conviene elevar el mínimo.
[System.Threading.ThreadPool]::SetMinThreads(200, 200) | Out-Null
$raizProyecto = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$marca = Get-Date -Format "yyyyMMdd-HHmmss"
$claveContrasena = "Carga#2026!ok"

# ------------------------------------------------------------------ configuración de MySQL
[xml]$contexto = Get-Content (Join-Path $raizProyecto "web\META-INF\context.xml")
$recurso = $contexto.Context.Resource
$bd = ([regex]::Match([string]$recurso.url, "3306/([^?]+)")).Groups[1].Value
$usuarioBd = [string]$recurso.username
$claveBd = [string]$recurso.password
$poolMaxTotal = [string]$recurso.maxTotal

function Sql-Ejecutar([string]$sql) {
    $previo = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $salida = & $MySqlExe "-u$usuarioBd" "-p$claveBd" "-e" ("USE $bd; " + $sql) 2>&1
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previo
    }
    $salida = @($salida | Where-Object { "$_" -notmatch "Using a password" })
    if ($codigo -ne 0) {
        throw ("Fallo SQL: " + ($salida | Out-String) + "`nSentencia: " + $sql)
    }
    return $salida
}

function Sql-Escalar([string]$sql) {
    $previo = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $salida = & $MySqlExe "-u$usuarioBd" "-p$claveBd" "-N" "-e" ("USE $bd; " + $sql) 2>&1
    } finally {
        $ErrorActionPreference = $previo
    }
    return ((@($salida | Where-Object { "$_" -notmatch "Using a password" }) -join "`n").Trim())
}

function Sql-In($valores) {
    return (($valores | ForEach-Object { [string]$_ }) -join ",")
}

function Token([string]$html) {
    return [regex]::Match($html, 'name="_csrf" value="([^"]+)"').Groups[1].Value
}

function Iniciar-Sesion([string]$correo, [string]$contrasena) {
    $sesion = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $login = Invoke-WebRequest "$BaseUrl/login" -WebSession $sesion -UseBasicParsing
    $token = Token $login.Content
    Invoke-WebRequest "$BaseUrl/login" -Method Post -WebSession $sesion -UseBasicParsing `
        -Body @{ correo = $correo; contrasena = $contrasena; _csrf = $token } | Out-Null
    return $sesion
}

# ------------------------------------------------------------------ manifiesto (para la limpieza)
$manifiesto = [ordered]@{
    usuarios = @(); docente = @(); curso = @(); grupo = @(); banco = @();
    preguntas = @(); evaluacion = @(); version = @(); versionPreguntas = @();
    opciones = @(); virtual = @(); inscripciones = @(); intentos = @()
}

function Limpiar {
    Write-Output "Limpiando datos de la corrida..."
    try {
        if ($manifiesto.intentos.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM respuesta WHERE id_intento IN (" + (Sql-In $manifiesto.intentos) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM evento_supervision WHERE id_intento IN (" + (Sql-In $manifiesto.intentos) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM intento WHERE id_intento IN (" + (Sql-In $manifiesto.intentos) + ");") | Out-Null
        }
        if ($manifiesto.virtual.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM evaluacion_virtual_version WHERE id_evaluacion_virtual IN (" + (Sql-In $manifiesto.virtual) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM evaluacion_virtual WHERE id_evaluacion_virtual IN (" + (Sql-In $manifiesto.virtual) + ");") | Out-Null
        }
        if ($manifiesto.versionPreguntas.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM version_opcion WHERE id_version_pregunta IN (" + (Sql-In $manifiesto.versionPreguntas) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM version_pregunta WHERE id_version_pregunta IN (" + (Sql-In $manifiesto.versionPreguntas) + ");") | Out-Null
        }
        if ($manifiesto.version.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM version_evaluacion WHERE id_version_evaluacion IN (" + (Sql-In $manifiesto.version) + ");") | Out-Null
        }
        if ($manifiesto.evaluacion.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM evaluacion_pregunta WHERE id_evaluacion IN (" + (Sql-In $manifiesto.evaluacion) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM evaluacion WHERE id_evaluacion IN (" + (Sql-In $manifiesto.evaluacion) + ");") | Out-Null
        }
        if ($manifiesto.preguntas.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM opcion_respuesta WHERE id_pregunta IN (" + (Sql-In $manifiesto.preguntas) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM pregunta WHERE id_pregunta IN (" + (Sql-In $manifiesto.preguntas) + ");") | Out-Null
        }
        if ($manifiesto.banco.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM banco_preguntas WHERE id_banco_preguntas IN (" + (Sql-In $manifiesto.banco) + ");") | Out-Null
        }
        if ($manifiesto.inscripciones.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM inscripcion WHERE id_inscripcion IN (" + (Sql-In $manifiesto.inscripciones) + ");") | Out-Null
        }
        if ($manifiesto.grupo.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM grupo WHERE id_grupo IN (" + (Sql-In $manifiesto.grupo) + ");") | Out-Null
        }
        if ($manifiesto.curso.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM curso WHERE id_curso IN (" + (Sql-In $manifiesto.curso) + ");") | Out-Null
        }
        if ($manifiesto.docente.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM docente WHERE id_usuario IN (" + (Sql-In $manifiesto.docente) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM token_recuperacion WHERE id_usuario IN (" + (Sql-In $manifiesto.docente) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM usuario WHERE id_usuario IN (" + (Sql-In $manifiesto.docente) + ");") | Out-Null
        }
        if ($manifiesto.usuarios.Count -gt 0) {
            Sql-Ejecutar ("DELETE FROM estudiante WHERE id_usuario IN (" + (Sql-In $manifiesto.usuarios) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM token_recuperacion WHERE id_usuario IN (" + (Sql-In $manifiesto.usuarios) + ");") | Out-Null
            Sql-Ejecutar ("DELETE FROM usuario WHERE id_usuario IN (" + (Sql-In $manifiesto.usuarios) + ");") | Out-Null
        }
        $restos = Sql-Escalar ("SELECT COUNT(*) FROM usuario WHERE correo LIKE '" + $Prefijo + "%'")
        Write-Output ("Limpieza terminada. Usuarios de prueba restantes: " + $restos)
    } catch {
        Write-Output ("ATENCIÓN: la limpieza falló: " + $_.Exception.Message)
        Write-Output ("Los registros llevan el prefijo '" + $Prefijo + "' para borrarlos a mano.")
    }
}

# ------------------------------------------------------------------ verificaciones previas
$restos = Sql-Escalar ("SELECT COUNT(*) FROM usuario WHERE correo LIKE '" + $Prefijo + "%'")
if ([int]$restos -gt 0) {
    throw ("Hay $restos usuarios de una corrida anterior ('" + $Prefijo + "...'). Bórralos antes de medir: " +
        "el script no toca datos que no creó en esta corrida.")
}
try {
    $prueba = Invoke-WebRequest "$BaseUrl/login" -UseBasicParsing -TimeoutSec 10
    if ($prueba.StatusCode -ne 200) { throw "Estado inesperado" }
} catch {
    throw ("No responde " + $BaseUrl + ". Arranca Tomcat y, para HTTP local, usa secure=false (ver README).")
}

# ------------------------------------------------------------------ entorno (queda en el informe)
$os = Get-CimInstance Win32_OperatingSystem
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$discos = (Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'").FreeSpace
$entorno = [ordered]@{
    fecha = (Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")
    zonaWindows = (tzutil /g | Out-String).Trim()
    sistemaOperativo = $os.Caption + " (" + $os.Version + ")"
    cpu = $cpu.Name
    nucleos = $cpu.NumberOfCores
    hilos = $cpu.NumberOfLogicalProcessors
    ramTotalMB = [math]::Round($os.TotalVisibleMemorySize / 1024)
    ramLibreMB = [math]::Round($os.FreePhysicalMemory / 1024)
    mysql = Sql-Escalar "SELECT VERSION()"
    poolMaxTotal = $poolMaxTotal
    procesosJava = @(Get-Process java -ErrorAction SilentlyContinue).Count
    navegadorDeCarga = "PowerShell " + $PSVersionTable.PSVersion + " (runspaces, mismo equipo que el servidor)"
}
$entorno.GetEnumerator() | ForEach-Object { Write-Output ("  " + $_.Key + ": " + $_.Value) }

$resultado = [ordered]@{
    marca = $marca
    parametros = @{ usuarios = $Usuarios; guardadosPorUsuario = $GuardadosPorUsuario; preguntas = $Preguntas }
    entorno = $entorno
}
$pdfGuardado = Join-Path $PSScriptRoot ("pdf-50-preguntas-" + $marca + ".pdf")

try {
    # -------------------------------------------------------------- datos de prueba
    Write-Output "Creando datos de prueba..."
    $correoBase = $Prefijo + "1@examlen.local"
    $sesionSemilla = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $paginaRegistro = Invoke-WebRequest "$BaseUrl/registro" -WebSession $sesionSemilla -UseBasicParsing
    Invoke-WebRequest "$BaseUrl/registro" -Method Post -WebSession $sesionSemilla -UseBasicParsing -Body @{
        identificacion = "990000001"; nombre = ("Carga Estudiante " + ("a" * 1)); correo = $correoBase
        nivel = "B1"; institucion = "Prueba de carga"; contrasena = $claveContrasena
        confirmacion = $claveContrasena; acepta = "true"; _csrf = (Token $paginaRegistro.Content)
    } | Out-Null
    $idBase = Sql-Escalar ("SELECT id_usuario FROM usuario WHERE correo='" + $correoBase + "'")
    if (-not $idBase) {
        throw "No se pudo registrar el estudiante semilla (revisa que el registro acepte la casilla de datos)."
    }
    $manifiesto.usuarios += $idBase
    $hash = Sql-Escalar ("SELECT contrasena_hash FROM usuario WHERE id_usuario=" + $idBase)

    $idAdmin = Sql-Escalar "SELECT MIN(id_usuario) FROM administrador"
    Sql-Ejecutar ("INSERT INTO usuario (identificacion, nombre, correo, institucion, contrasena_hash, activo) " +
        "VALUES ('990000050', 'Docente Carga', '" + $Prefijo + "docente@examlen.local', 'Prueba de carga', '" +
        $hash + "', TRUE)") | Out-Null
    $idDocente = Sql-Escalar ("SELECT id_usuario FROM usuario WHERE correo='" + $Prefijo + "docente@examlen.local'")
    $manifiesto.docente += $idDocente
    Sql-Ejecutar ("INSERT INTO docente (id_usuario, debe_cambiar_contrasena, id_administrador) VALUES (" +
        $idDocente + ", FALSE, " + $idAdmin + ")") | Out-Null

    for ($i = 2; $i -le $Usuarios; $i++) {
        $identificacion = 990000000 + $i
        $nombre = "Carga Estudiante " + ("a" * $i)
        Sql-Ejecutar ("INSERT INTO usuario (identificacion, nombre, correo, contrasena_hash, activo) VALUES ('" +
            $identificacion + "', '" + $nombre + "', '" + $Prefijo + $i + "@examlen.local', '" +
            $hash + "', TRUE)") | Out-Null
        $id = Sql-Escalar ("SELECT id_usuario FROM usuario WHERE correo='" + $Prefijo + $i + "@examlen.local'")
        $manifiesto.usuarios += $id
        Sql-Ejecutar ("INSERT INTO estudiante (id_usuario, nivel_ingles, aceptacion_tratamiento_datos) VALUES (" +
            $id + ", 'B1', NOW())") | Out-Null
    }

    Sql-Ejecutar ("INSERT INTO curso (nombre, descripcion, nivel, activo, id_docente) VALUES ('Curso " + $marca +
        "', 'Prueba de carga', 'B1', TRUE, " + $idDocente + ")") | Out-Null
    $idCurso = Sql-Escalar ("SELECT id_curso FROM curso WHERE nombre='Curso " + $marca + "'")
    $manifiesto.curso += $idCurso
    Sql-Ejecutar ("INSERT INTO grupo (nombre, periodo, codigo_acceso, estado, id_curso) VALUES ('Grupo " + $marca +
        "', '2026-1', 'CRG" + $marca.Substring(9, 5) + "', 'ABIERTO', " + $idCurso + ")") | Out-Null
    $idGrupo = Sql-Escalar ("SELECT id_grupo FROM grupo WHERE nombre='Grupo " + $marca + "'")
    $manifiesto.grupo += $idGrupo
    Sql-Ejecutar ("INSERT INTO banco_preguntas (nombre, descripcion, id_docente) VALUES ('Banco " + $marca +
        "', 'Prueba de carga', " + $idDocente + ")") | Out-Null
    $idBanco = Sql-Escalar ("SELECT id_banco_preguntas FROM banco_preguntas WHERE nombre='Banco " + $marca + "'")
    $manifiesto.banco += $idBanco

    for ($i = 1; $i -le $Preguntas; $i++) {
        Sql-Ejecutar ("INSERT INTO pregunta (enunciado, tipo, nivel, dificultad, competencia, tema, " +
            "respuesta_esperada, estado, origen, id_banco_preguntas) VALUES ('Pregunta " + $i +
            " de la prueba de carga con un enunciado de longitud media para el PDF', 'SELECCION_MULTIPLE', " +
            "'B1', 'MEDIA', 'GRAMMAR', 'Carga', NULL, 'APROBADA', 'MANUAL', " + $idBanco + ")") | Out-Null
    }
    $textoIdsPreguntas = Sql-Escalar ("SELECT GROUP_CONCAT(id_pregunta ORDER BY id_pregunta) FROM pregunta " +
        "WHERE id_banco_preguntas=" + $idBanco)
    $idsPreguntas = @($textoIdsPreguntas.Split(",") | Where-Object { $_ -ne "" })
    $manifiesto.preguntas = $idsPreguntas
    foreach ($idPregunta in $idsPreguntas) {
        Sql-Ejecutar ("INSERT INTO opcion_respuesta (texto, es_correcta, id_pregunta) VALUES ('Opcion correcta', TRUE, " +
            $idPregunta + "), ('Opcion incorrecta', FALSE, " + $idPregunta + ")") | Out-Null
    }

    Sql-Ejecutar ("INSERT INTO evaluacion (titulo, descripcion, numero_preguntas, numero_versiones, " +
        "duracion_minutos, estado, id_docente) VALUES ('Evaluacion " + $marca + "', 'Prueba de carga', " +
        $Preguntas + ", 1, 30, 'LISTA', " + $idDocente + ")") | Out-Null
    $idEvaluacion = Sql-Escalar ("SELECT id_evaluacion FROM evaluacion WHERE titulo='Evaluacion " + $marca + "'")
    $manifiesto.evaluacion += $idEvaluacion
    Sql-Ejecutar ("INSERT INTO version_evaluacion (codigo, fecha_generacion, id_evaluacion) VALUES ('A', NOW(), " +
        $idEvaluacion + ")") | Out-Null
    $idVersion = Sql-Escalar ("SELECT id_version_evaluacion FROM version_evaluacion WHERE id_evaluacion=" + $idEvaluacion)
    $manifiesto.version += $idVersion
    $orden = 0
    foreach ($idPregunta in $idsPreguntas) {
        $orden++
        Sql-Ejecutar ("INSERT INTO version_pregunta (enunciado, respuesta_esperada, orden, puntaje, " +
            "id_version_evaluacion, id_pregunta) SELECT enunciado, respuesta_esperada, " + $orden + ", 10, " +
            $idVersion + ", id_pregunta FROM pregunta WHERE id_pregunta=" + $idPregunta) | Out-Null
    }
    $textoIdsVersionPreguntas = Sql-Escalar ("SELECT GROUP_CONCAT(id_version_pregunta ORDER BY orden) FROM " +
        "version_pregunta WHERE id_version_evaluacion=" + $idVersion)
    $idsVersionPreguntas = @($textoIdsVersionPreguntas.Split(",") | Where-Object { $_ -ne "" })
    $manifiesto.versionPreguntas = $idsVersionPreguntas
    foreach ($idVersionPregunta in $idsVersionPreguntas) {
        Sql-Ejecutar ("INSERT INTO version_opcion (texto, es_correcta, orden, id_version_pregunta) VALUES " +
            "('Opcion correcta', TRUE, 1, " + $idVersionPregunta + "), ('Opcion incorrecta', FALSE, 2, " +
            $idVersionPregunta + ")") | Out-Null
    }
    $textoIdsOpciones = Sql-Escalar ("SELECT GROUP_CONCAT(id_version_opcion ORDER BY id_version_pregunta, orden) " +
        "FROM version_opcion WHERE id_version_pregunta IN (" + (Sql-In $idsVersionPreguntas) + ") AND es_correcta = TRUE")
    $idsOpciones = @($textoIdsOpciones.Split(",") | Where-Object { $_ -ne "" })
    $manifiesto.opciones = $idsOpciones

    Sql-Ejecutar ("INSERT INTO evaluacion_virtual (fecha_inicio, fecha_fin, supervision_activa, estado, " +
        "id_grupo, id_evaluacion) VALUES (DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 6 HOUR), " +
        "FALSE, 'ABIERTA', " + $idGrupo + ", " + $idEvaluacion + ")") | Out-Null
    $idVirtual = Sql-Escalar ("SELECT id_evaluacion_virtual FROM evaluacion_virtual WHERE id_grupo=" + $idGrupo)
    $manifiesto.virtual += $idVirtual
    Sql-Ejecutar ("INSERT INTO evaluacion_virtual_version (id_evaluacion_virtual, id_version_evaluacion) VALUES (" +
        $idVirtual + ", " + $idVersion + ")") | Out-Null

    foreach ($idEstudiante in $manifiesto.usuarios) {
        Sql-Ejecutar ("INSERT INTO inscripcion (fecha, estado, id_grupo, id_estudiante) VALUES (NOW(), 'ACTIVA', " +
            $idGrupo + ", " + $idEstudiante + ")") | Out-Null
        $idInscripcion = Sql-Escalar ("SELECT id_inscripcion FROM inscripcion WHERE id_grupo=" + $idGrupo +
            " AND id_estudiante=" + $idEstudiante)
        $manifiesto.inscripciones += $idInscripcion
        Sql-Ejecutar ("INSERT INTO intento (id_evaluacion_virtual, id_inscripcion, id_version_evaluacion, estado) " +
            "VALUES (" + $idVirtual + ", " + $idInscripcion + ", " + $idVersion + ", 'EN_CURSO')") | Out-Null
        $idIntento = Sql-Escalar ("SELECT id_intento FROM intento WHERE id_inscripcion=" + $idInscripcion)
        $manifiesto.intentos += $idIntento
    }
    Write-Output ("Datos listos: " + $Usuarios + " intentos, versión con " + $Preguntas + " preguntas, virtual " + $idVirtual)

    # -------------------------------------------------------------- carga concurrente
    $barrera = New-Object System.Threading.ManualResetEventSlim($false)
    $listos = New-Object 'System.Collections.Concurrent.BlockingCollection[string]'
    $ases = [System.Management.Automation.Runspaces.InitialSessionState]::CreateDefault()
    $grupoRunspaces = [runspacefactory]::CreateRunspacePool(1, $Usuarios, $ases, $Host)
    $grupoRunspaces.Open()

    $trabajador = {
        param($cfg)
        $ProgressPreference = "SilentlyContinue"
        $resultado = [pscustomobject]@{
            correo = $cfg.Correo; ok = $false; error = ""
            guardados = @(); paginaMs = 0
        }
        try {
            $sesion = New-Object Microsoft.PowerShell.Commands.WebRequestSession
            $login = Invoke-WebRequest "$($cfg.BaseUrl)/login" -WebSession $sesion -UseBasicParsing
            Invoke-WebRequest "$($cfg.BaseUrl)/login" -Method Post -WebSession $sesion -UseBasicParsing -Body @{
                correo = $cfg.Correo; contrasena = $cfg.Contrasena; _csrf = [regex]::Match($login.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
            } | Out-Null
            $examen = Invoke-WebRequest "$($cfg.BaseUrl)/app/estudiante/intento?id=$($cfg.Intento)" -WebSession $sesion -UseBasicParsing
            $token = [regex]::Match($examen.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
            $cfg.Listos.Add($cfg.Correo)
            $cfg.Barrera.Wait()
            $cronometro = New-Object System.Diagnostics.Stopwatch
            $latencias = New-Object System.Collections.ArrayList
            for ($i = 0; $i -lt $cfg.Guardados; $i++) {
                $indice = $i % $cfg.VersionPreguntas.Count
                $cronometro.Restart()
                Invoke-WebRequest "$($cfg.BaseUrl)/app/estudiante/intento" -Method Post -WebSession $sesion -UseBasicParsing -Body @{
                    accion = "responder"; id = $cfg.Intento
                    idVersionPregunta = $cfg.VersionPreguntas[$indice]; idOpcion = $cfg.Opciones[$indice]
                    _csrf = $token
                } | Out-Null
                $cronometro.Stop()
                [void]$latencias.Add($cronometro.Elapsed.TotalMilliseconds)
            }
            $cronometro.Restart()
            Invoke-WebRequest "$($cfg.BaseUrl)/app/estudiante/evaluaciones" -WebSession $sesion -UseBasicParsing | Out-Null
            $cronometro.Stop()
            $resultado.guardados = $latencias.ToArray()
            $resultado.paginaMs = $cronometro.Elapsed.TotalMilliseconds
            $resultado.ok = $true
        } catch {
            $resultado.error = $_.Exception.Message
        }
        return $resultado
    }

    $ejecuciones = @()
    $idsIntentos = @($manifiesto.intentos)
    for ($i = 0; $i -lt $Usuarios; $i++) {
        $cfg = [pscustomobject]@{
            BaseUrl = $BaseUrl; Correo = ($Prefijo + ($i + 1) + "@examlen.local"); Contrasena = $claveContrasena
            Intento = $idsIntentos[$i]
            VersionPreguntas = @($idsVersionPreguntas[($i % 5)..(($i % 5) + 4)])
            Opciones = @($idsOpciones[($i % 5)..(($i % 5) + 4)])
            Barrera = $barrera; Listos = $listos; Guardados = $GuardadosPorUsuario
        }
        $ps = [powershell]::Create()
        $ps.RunspacePool = $grupoRunspaces
        [void]$ps.AddScript($trabajador.ToString()).AddArgument($cfg)
        $ejecuciones += [pscustomobject]@{ Ps = $ps; Handle = $ps.BeginInvoke() }
    }
    $preparados = 0
    $elemento = $null
    while ($preparados -lt $Usuarios -and $listos.TryTake([ref]$elemento, 180000)) {
        $preparados++
    }
    if ($preparados -lt $Usuarios) {
        throw ("Solo " + $preparados + " de " + $Usuarios + " estudiantes iniciaron sesión. Revisa Tomcat.")
    }
    Write-Output ("Los " + $Usuarios + " estudiantes están listos; comienza la carga...")
    $inicioCarga = Get-Date
    $barrera.Set()

    $resultadosTrabajadores = @()
    foreach ($ejecucion in $ejecuciones) {
        try {
            $resultadosTrabajadores += $ejecucion.Ps.EndInvoke($ejecucion.Handle)
        } catch {
            $resultadosTrabajadores += [pscustomobject]@{ correo = "?"; ok = $false; error = $_.Exception.Message; guardados = @(); paginaMs = 0 }
        }
        $ejecucion.Ps.Dispose()
    }
    $duracionCarga = ((Get-Date) - $inicioCarga).TotalSeconds

    $todas = @($resultadosTrabajadores | Where-Object { $_.ok } | ForEach-Object { $_.guardados } | ForEach-Object { [double]$_ })
    $paginas = @($resultadosTrabajadores | Where-Object { $_.ok } | ForEach-Object { [double]$_.paginaMs })
    $fallidos = @($resultadosTrabajadores | Where-Object { -not $_.ok })
    $ordenadas = @($todas | Sort-Object)
    function Percentil($valores, [double]$p) {
        if ($valores.Count -eq 0) { return 0 }
        $indice = [Math]::Ceiling($p * $valores.Count) - 1
        if ($indice -lt 0) { $indice = 0 }
        return $valores[$indice]
    }
    $resumenGuardados = @{
        peticiones = $todas.Count
        p50 = [math]::Round((Percentil $ordenadas 0.50), 1)
        p95 = [math]::Round((Percentil $ordenadas 0.95), 1)
        max = [math]::Round((Percentil $ordenadas 1.0), 1)
        errores = $fallidos.Count
    }
    $resumenPaginas = @{
        peticiones = $paginas.Count
        p50 = [math]::Round((Percentil ($paginas | Sort-Object) 0.50), 1)
        p95 = [math]::Round((Percentil ($paginas | Sort-Object) 0.95), 1)
        max = [math]::Round((Percentil ($paginas | Sort-Object) 1.0), 1)
    }

    # PDF de 50 preguntas (RNF-08): tres mediciones con la sesión del docente
    $sesionDocente = Iniciar-Sesion ($Prefijo + "docente@examlen.local") $claveContrasena
    $tiemposPdf = @()
    for ($i = 1; $i -le 3; $i++) {
        $cronometro = New-Object System.Diagnostics.Stopwatch
        $cronometro.Start()
        $pdf = Invoke-WebRequest ("$BaseUrl/app/docente/version/pdf?id=" + $idVersion) -WebSession $sesionDocente -UseBasicParsing
        $cronometro.Stop()
        $tiemposPdf += [math]::Round($cronometro.Elapsed.TotalMilliseconds, 1)
    }
    [System.IO.File]::WriteAllBytes($pdfGuardado, $pdf.Content)

    $resultado.carga = @{
        duracionSegundos = [math]::Round($duracionCarga, 1)
        guardados = $resumenGuardados
        paginas = $resumenPaginas
        pdfMs = $tiemposPdf
        pdfBytes = $pdf.Content.Length
        trabajadoresFallidos = @($fallidos | ForEach-Object { $_.correo + ": " + $_.error })
    }
    $resultado.veredictos = @{
        rnf07_guardadoP95Menor2s = ($resumenGuardados.p95 -lt 2000 -and $resumenGuardados.errores -eq 0)
        rnf08_paginaP95Menor3s = ($resumenPaginas.p95 -lt 3000)
        rnf08_pdfMenor10s = (($tiemposPdf | Measure-Object -Maximum).Maximum -lt 10000)
    }

    $archivo = Join-Path $PSScriptRoot ("resultados-" + $marca + ".json")
    [System.IO.File]::WriteAllText($archivo, ($resultado | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))
    Write-Output ""
    Write-Output "================ RESULTADO ================"
    Write-Output ("Guardados (n=" + $resumenGuardados.peticiones + "): p50=" + $resumenGuardados.p50 + " ms  p95=" +
        $resumenGuardados.p95 + " ms  max=" + $resumenGuardados.max + " ms  errores=" + $resumenGuardados.errores)
    Write-Output ("Páginas de consulta (n=" + $resumenPaginas.peticiones + "): p50=" + $resumenPaginas.p50 + " ms  p95=" +
        $resumenPaginas.p95 + " ms  max=" + $resumenPaginas.max + " ms")
    Write-Output ("PDF 50 preguntas: " + ($tiemposPdf -join " / ") + " ms  (" + $pdf.Content.Length + " bytes)  guardado en " + (Split-Path $pdfGuardado -Leaf))
    Write-Output ("Veredictos: RNF-07 p95<2s=" + $resultado.veredictos.rnf07_guardadoP95Menor2s +
        " | RNF-08 páginas<3s=" + $resultado.veredictos.rnf08_paginaP95Menor3s +
        " | RNF-08 PDF<10s=" + $resultado.veredictos.rnf08_pdfMenor10s)
    Write-Output ("Resultados guardados en " + (Split-Path $archivo -Leaf))
} finally {
    Limpiar
    if ($grupoRunspaces) { $grupoRunspaces.Close(); $grupoRunspaces.Dispose() }
}
