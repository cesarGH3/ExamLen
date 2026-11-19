"""Exporta ExamLen-estructura.sql desde una BD de pruebas con 001-023 aplicadas.

Uso:
  EXAMLEN_EXPORT_HOST=127.0.0.1 EXAMLEN_EXPORT_PORT=3310 \
  EXAMLEN_EXPORT_USER=... EXAMLEN_EXPORT_PASSWORD=... EXAMLEN_EXPORT_DB=examlen \
  python pruebas/exportar_estructura.py [salida]

El resultado contiene solo esquema: tablas, indices, FK, CHECK y triggers. No incluye
filas de negocio ni credenciales. Los DEFINER se omiten para que la importacion use
la cuenta que ejecuta el script.
"""
import os
from pathlib import Path
import subprocess
import sys

MYSQL = os.environ.get("MYSQL_BIN", "mysql")
HOST = os.environ.get("EXAMLEN_EXPORT_HOST", "127.0.0.1")
PORT = os.environ.get("EXAMLEN_EXPORT_PORT", "3306")
USER = os.environ.get("EXAMLEN_EXPORT_USER", "root")
PASSWORD = os.environ.get("EXAMLEN_EXPORT_PASSWORD", "")
DB = os.environ.get("EXAMLEN_EXPORT_DB", "examlen")
SALIDA = Path(sys.argv[1] if len(sys.argv) > 1 else "database/ExamLen-estructura.sql")


def mysql(sql, base=None):
    comando = [MYSQL, "--batch", "--raw", "--skip-column-names",
               "--host", HOST, "--port", PORT, "--user", USER]
    if PASSWORD:
        comando.append("--password=" + PASSWORD)
    if base:
        comando.append("--database=" + base)
    comando.extend(["-e", sql])
    resultado = subprocess.run(comando, check=True, capture_output=True)
    return resultado.stdout.decode("utf-8", errors="replace")


def filas(sql, base=DB):
    return [line for line in mysql(sql, base).splitlines() if line != ""]


def identificador(nombre):
    return "`" + nombre.replace("`", "``") + "`"


def orden_topologico(tablas, dependencias):
    pendientes = set(tablas)
    orden = []
    while pendientes:
        listas = [t for t in pendientes if not (dependencias.get(t, set()) & pendientes)]
        if not listas:
            return None
        for tabla in sorted(listas):
            orden.append(tabla)
            pendientes.remove(tabla)
    return orden


def main():
    tablas = filas("SELECT TABLE_NAME FROM information_schema.TABLES "
                   "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")
    if not tablas:
        raise SystemExit("La base no tiene tablas.")

    for tabla in tablas:
        if tabla != "esquema_migracion":
            total = int(filas("SELECT COUNT(*) FROM " + identificador(tabla))[0])
            if total:
                raise SystemExit(f"La tabla {tabla} tiene {total} filas; usa una BD solo de esquema.")

    migraciones = filas("SELECT nombre FROM esquema_migracion ORDER BY nombre")
    if not migraciones:
        raise SystemExit("Falta la tabla esquema_migracion con las migraciones aplicadas.")
    if migraciones[-1] != "023_roles_sin_columna_tipo.sql":
        raise SystemExit("La BD no tiene aplicada la migracion 023.")

    dependencias = {tabla: set() for tabla in tablas}
    for linea in filas("SELECT TABLE_NAME, REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE "
                       "WHERE CONSTRAINT_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL "
                       "AND TABLE_NAME <> REFERENCED_TABLE_NAME"):
        tabla, referenciada = linea.split("\t", 1)
        dependencias.setdefault(tabla, set()).add(referenciada)

    orden = orden_topologico(tablas, dependencias)
    ciclo = orden is None
    if ciclo:
        orden = sorted(tablas)

    partes = []
    partes.append("-- =====================================================================")
    partes.append("-- ExamLen - Estructura de la base de datos (solo esquema, sin datos)")
    partes.append("-- Generado desde una BD de pruebas con las migraciones 001 a 023.")
    partes.append("--")
    partes.append("-- Importacion segura:")
    partes.append("--   * No contiene sentencias destructivas de tablas, bases ni registros.")
    partes.append("--   * Conserva indices, claves foraneas, CHECK y triggers.")
    partes.append("--   * Los triggers no llevan DEFINER: se crean con la cuenta que importa.")
    partes.append("--   * No contiene usuarios, contrasenas ni datos personales.")
    partes.append("-- =====================================================================")
    partes.append("")
    partes.append("CREATE DATABASE IF NOT EXISTS " + identificador(DB)
                  + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
    partes.append("USE " + identificador(DB) + ";")
    partes.append("")
    if ciclo:
        partes.append("SET FOREIGN_KEY_CHECKS = 0;")
        partes.append("SET UNIQUE_CHECKS = 0;")
        partes.append("")
    else:
        partes.append("SET FOREIGN_KEY_CHECKS = 1;")
        partes.append("SET UNIQUE_CHECKS = 1;")
        partes.append("")

    for tabla in orden:
        salida = mysql("SHOW CREATE TABLE " + identificador(tabla), DB)
        if "\t" in salida:
            salida = salida.split("\t", 1)[1]
        salida = salida.replace("\r\n", "\n").replace("\r", "\n").strip()
        import re
        salida = re.sub(r"\s+AUTO_INCREMENT=\d+", "", salida)
        partes.append("-- Tabla " + tabla)
        partes.append(salida + ";")
        partes.append("")

    triggers = filas("SELECT TRIGGER_NAME, ACTION_TIMING, EVENT_MANIPULATION, EVENT_OBJECT_TABLE "
                     "FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() "
                     "ORDER BY TRIGGER_NAME")
    if triggers:
        partes.append("-- Triggers (sin DEFINER para importacion reproducible)")
        partes.append("DELIMITER $$")
        for linea in triggers:
            nombre, momento, evento, tabla = linea.split("\t")
            cuerpo = mysql("SELECT ACTION_STATEMENT FROM information_schema.TRIGGERS "
                           "WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME = '"
                           + nombre.replace("'", "''") + "'", DB)
            cuerpo = cuerpo.replace("\r\n", "\n").replace("\r", "\n").strip()
            partes.append("CREATE TRIGGER " + identificador(nombre) + " " + momento + " " + evento
                          + " ON " + identificador(tabla) + " FOR EACH ROW")
            partes.append(cuerpo + "$$")
        partes.append("DELIMITER ;")
        partes.append("")

    if ciclo:
        partes.append("SET FOREIGN_KEY_CHECKS = 1;")
        partes.append("SET UNIQUE_CHECKS = 1;")
        partes.append("")

    partes.append("-- Migraciones aplicadas (tabla tecnica, sin datos de negocio)")
    for nombre in migraciones:
        partes.append("INSERT INTO " + identificador("esquema_migracion")
                      + " (nombre) VALUES ('" + nombre.replace("'", "''") + "');")
    partes.append("")

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    texto = "\n".join(partes) + "\n"
    texto = texto.replace("\r\n", "\n").replace("\r", "\n")
    with SALIDA.open("w", encoding="utf-8", newline="\n") as archivo:
        archivo.write(texto)
    print(f"Exportado {SALIDA} ({len(tablas)} tablas, {len(triggers)} triggers, "
          f"{len(migraciones)} migraciones,{' con' if ciclo else ' sin'} desactivar FK temporalmente).")


if __name__ == "__main__":
    main()
