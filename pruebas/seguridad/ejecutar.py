"""Compila fuentes Java 17 y ejecuta pruebas. Requiere JDK y Servlet API 5 externo.

Opcional: ECJ_JAR permite compilar con Eclipse ECJ en lugar de javac.
EXAMLEN_TEST_SERVER=jdbc:mysql://127.0.0.1:3306/ habilita pruebas MySQL 8.4.
La BD y la cuenta temporales se crean y eliminan en el servidor de pruebas.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

raiz = Path(__file__).resolve().parents[2]
os.chdir(raiz)
servlet = os.environ.get("SERVLET_API_JAR")
if not servlet or not Path(servlet).is_file():
    raise SystemExit("Define SERVLET_API_JAR con la ruta absoluta de jakarta.servlet-api-5.0.0.jar (o servlet-api.jar de Tomcat 10).")
java = shutil.which("java")
if not java:
    raise SystemExit("Instala un JDK 17 o posterior y configura PATH.")
classpath = os.pathsep.join([str(p.resolve()) for p in Path("web/WEB-INF/lib").glob("*.jar")] + [str(Path(servlet).resolve())])
fuentes = sorted(str(p) for carpeta in (Path("src/java"), Path("pruebas/seguridad")) for p in carpeta.rglob("*.java"))
with tempfile.TemporaryDirectory(prefix="examlen-seguridad-") as temporal:
    ecj = os.environ.get("ECJ_JAR")
    if ecj:
        compilador = [java, "-jar", ecj, "-17", "-warn:none"]
    else:
        javac = shutil.which("javac")
        if not javac:
            raise SystemExit("No se encuentra javac en PATH.")
        compilador = [javac, "--release", "17"]
    subprocess.run(compilador + ["-encoding", "UTF-8", "-cp", classpath, "-d", temporal] + fuentes, check=True)
    subprocess.run([java, "-cp", temporal + os.pathsep + classpath, "api.SeguridadRegression"], check=True)
