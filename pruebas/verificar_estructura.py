"""Importa ExamLen-estructura.sql en una BD vacia y compara la estructura.

Uso:
  EXAMLEN_EXPORT_*=... python pruebas/verificar_estructura.py [sql]

Requiere la misma instancia MySQL aislada usada para exportar. No toca la BD origen.
"""
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

MYSQL = os.environ.get("MYSQL_BIN", "mysql")
HOST = os.environ.get("EXAMLEN_EXPORT_HOST", "127.0.0.1")
PORT = os.environ.get("EXAMLEN_EXPORT_PORT", "3306")
USER = os.environ.get("EXAMLEN_EXPORT_USER", "root")
PASSWORD = os.environ.get("EXAMLEN_EXPORT_PASSWORD", "")
ORIGEN = os.environ.get("EXAMLEN_EXPORT_DB", "examlen")
SQL = Path(sys.argv[1] if len(sys.argv) > 1 else "database/ExamLen-estructura.sql")
DESTINO = "examlen_verify_" + uuid.uuid4().hex


def mysql(sql, base=None):
    comando = [MYSQL, "--batch", "--raw", "--skip-column-names",
               "--host", HOST, "--port", PORT, "--user", USER]
    if PASSWORD:
        comando.append("--password=" + PASSWORD)
    if base:
        comando.append("--database=" + base)
    comando.extend(["-e", sql])
    return subprocess.run(comando, check=True, capture_output=True).stdout.decode("utf-8", errors="replace")


def filas(sql, base):
    return [linea for linea in mysql(sql, base).replace("\r\n", "\n").split("\n") if linea != ""]


def comparar(nombre, consulta):
    a = sorted(filas(consulta.format(base=ORIGEN), ORIGEN))
    b = sorted(filas(consulta.format(base=DESTINO), DESTINO))
    if a != b:
        faltan = [x for x in a if x not in b][:5]
        sobran = [x for x in b if x not in a][:5]
        raise SystemExit(f"Diferencia en {nombre}. Faltan: {faltan}. Sobran: {sobran}.")
    print(f"OK {nombre}: {len(a)} elementos")


def main():
    texto = SQL.read_text(encoding="utf-8")
    if re.search(r"(?i)\bDEFINER\s*=", texto):
        raise SystemExit("El SQL contiene DEFINER; la importacion no seria reproducible.")
    if re.search(r"(?i)\bCREATE\s+USER\b|\bGRANT\b|\bIDENTIFIED\s+BY\b", texto):
        raise SystemExit("El SQL contiene usuarios o credenciales.")
    if re.search(r"(?i)\bINSERT\s+INTO\b", texto):
        inserts = re.findall(r"(?i)\bINSERT\s+INTO\s+`([^`]+)`", texto)
        if any(tabla != "esquema_migracion" for tabla in inserts):
            raise SystemExit("El SQL contiene INSERT de datos de negocio.")

    mysql(f"DROP DATABASE IF EXISTS `{DESTINO}`; CREATE DATABASE `{DESTINO}` "
          "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
    # El archivo crea/usa examlen; se redirige a la BD de verificacion.
    importado = texto.replace("`" + ORIGEN + "`", "`" + DESTINO + "`")
    comando = [MYSQL, "--host", HOST, "--port", PORT, "--user", USER, DESTINO]
    if PASSWORD:
        comando.append("--password=" + PASSWORD)
    subprocess.run(comando, input=importado.encode("utf-8"), check=True)

    comparar("tablas", "SELECT TABLE_NAME FROM information_schema.TABLES "
                       "WHERE TABLE_SCHEMA='{base}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")
    comparar("columnas", "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
                         "IFNULL(COLUMN_DEFAULT,'<NULL>'), EXTRA FROM information_schema.COLUMNS "
                         "WHERE TABLE_SCHEMA='{base}' ORDER BY TABLE_NAME, ORDINAL_POSITION")
    comparar("indices", "SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE "
                        "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='{base}' "
                        "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX")
    comparar("claves foraneas", "SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, "
                                "REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                                "FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA='{base}' "
                                "AND REFERENCED_TABLE_NAME IS NOT NULL "
                                "ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION")
    comparar("check", "SELECT t.TABLE_NAME, c.CONSTRAINT_NAME, "
                      "REPLACE(REPLACE(c.CHECK_CLAUSE, CHAR(10), ' '), CHAR(13), ' ') "
                      "FROM information_schema.CHECK_CONSTRAINTS c "
                      "JOIN information_schema.TABLE_CONSTRAINTS t "
                      "ON t.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=c.CONSTRAINT_NAME "
                      "WHERE c.CONSTRAINT_SCHEMA='{base}' ORDER BY t.TABLE_NAME, c.CONSTRAINT_NAME")
    comparar("triggers", "SELECT TRIGGER_NAME, ACTION_TIMING, EVENT_MANIPULATION, EVENT_OBJECT_TABLE, "
                         "REPLACE(REPLACE(ACTION_STATEMENT, CHAR(10), ' '), CHAR(13), ' ') "
                         "FROM information_schema.TRIGGERS "
                         "WHERE TRIGGER_SCHEMA='{base}' ORDER BY TRIGGER_NAME")

    total_migraciones = int(filas("SELECT COUNT(*) FROM esquema_migracion", DESTINO)[0])
    if total_migraciones != 23:
        raise SystemExit(f"Se esperaban 23 migraciones y hay {total_migraciones}.")
    for tabla in filas("SELECT TABLE_NAME FROM information_schema.TABLES "
                       "WHERE TABLE_SCHEMA='" + DESTINO + "' AND TABLE_TYPE='BASE TABLE'", DESTINO):
        if tabla == "esquema_migracion":
            continue
        total = int(filas("SELECT COUNT(*) FROM `" + tabla + "`", DESTINO)[0])
        if total != 0:
            raise SystemExit(f"La tabla {tabla} tiene {total} filas tras importar.")
    print("OK datos: 23 migraciones y cero filas de negocio")

    mysql(f"DROP DATABASE IF EXISTS `{DESTINO}`;")
    print("OK importacion verificada y BD temporal eliminada.")


if __name__ == "__main__":
    main()
