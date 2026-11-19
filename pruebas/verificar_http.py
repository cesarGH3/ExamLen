"""Smoke test HTTP/HTTPS de ExamLen sobre un Tomcat de pruebas.

Requiere EXAMLEN_HTTP_CA (CA del certificado de Tomcat) y EXAMLEN_HTTP_BOOTSTRAP.
No contiene secretos reales. Usa una BD aislada y desechable.
"""
import http.cookiejar
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("EXAMLEN_HTTP_BASE", "https://localhost:8443/ExamLen")
CA = os.environ.get("EXAMLEN_HTTP_CA")
BOOTSTRAP = os.environ.get("EXAMLEN_HTTP_BOOTSTRAP")
ADMIN_USER = "admin.tomcat@test.invalid"
ADMIN_PASS = "Admin1234"
STUDENT_USER = "est.tomcat@test.invalid"
STUDENT_PASS = "Est12345"
VERIFICACIONES = 0


class SinRedireccion(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Sesion:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        contexto = ssl.create_default_context(cafile=CA)
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPSHandler(context=contexto),
            urllib.request.HTTPCookieProcessor(self.jar),
            SinRedireccion())

    def pedir(self, metodo, ruta, datos=None, cabeceras=None, json_texto=None):
        cuerpo = None
        if json_texto is not None:
            cuerpo = json_texto.encode("utf-8")
        elif datos is not None:
            cuerpo = urllib.parse.urlencode(datos).encode("utf-8")
        peticion = urllib.request.Request(BASE + ruta, data=cuerpo, method=metodo)
        if json_texto is not None:
            peticion.add_header("Content-Type", "application/json")
        for clave, valor in (cabeceras or {}).items():
            peticion.add_header(clave, valor)
        try:
            respuesta = self.opener.open(peticion, timeout=30)
            return respuesta.getcode(), respuesta.read().decode("utf-8", errors="replace"), respuesta.headers
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode("utf-8", errors="replace"), error.headers


def comprobar(condicion, caso):
    global VERIFICACIONES
    VERIFICACIONES += 1
    if not condicion:
        raise AssertionError("FALLO: " + caso)
    print(f"OK {VERIFICACIONES}: {caso}")


def csrf(cuerpo):
    encontrado = re.search(r'name="_csrf" value="([^"]+)"', cuerpo)
    if not encontrado:
        raise AssertionError("No se encontro token CSRF")
    return encontrado.group(1)


def main():
    if not CA or not BOOTSTRAP:
        raise SystemExit("Define EXAMLEN_HTTP_CA y EXAMLEN_HTTP_BOOTSTRAP.")
    anonimo = Sesion()

    codigo, cuerpo, _ = anonimo.pedir("GET", "/primer-administrador")
    comprobar(codigo == 200 and "claveInstalacion" in cuerpo, "Bootstrap disponible antes del primer administrador")
    token = csrf(cuerpo)

    codigo, _, _ = anonimo.pedir("POST", "/primer-administrador", {
        "identificacion": "ADMIN001", "nombre": "Admin Tomcat",
        "correo": ADMIN_USER, "contrasena": ADMIN_PASS, "confirmacion": ADMIN_PASS,
        "claveInstalacion": BOOTSTRAP})
    comprobar(codigo == 403, "POST sin CSRF rechazado")

    codigo, _, _ = anonimo.pedir("POST", "/primer-administrador", {
        "_csrf": token, "identificacion": "ADMIN001", "nombre": "Admin Tomcat",
        "correo": ADMIN_USER, "contrasena": ADMIN_PASS, "confirmacion": ADMIN_PASS,
        "claveInstalacion": "credencial-incorrecta"})
    comprobar(codigo == 403, "Bootstrap con credencial incorrecta rechazado")

    codigo, _, cabeceras = anonimo.pedir("POST", "/primer-administrador", {
        "_csrf": token, "identificacion": "ADMIN001", "nombre": "Admin Tomcat",
        "correo": ADMIN_USER, "contrasena": ADMIN_PASS, "confirmacion": ADMIN_PASS,
        "claveInstalacion": BOOTSTRAP})
    comprobar(codigo == 302, "Bootstrap correcto crea el administrador")

    codigo, _, _ = anonimo.pedir("GET", "/primer-administrador")
    comprobar(codigo == 404, "Bootstrap se deshabilita al existir administrador")

    codigo, cuerpo, _ = anonimo.pedir("GET", "/login")
    comprobar(codigo == 200, "Login disponible")
    token = csrf(cuerpo)
    codigo, _, _ = anonimo.pedir("POST", "/login", {
        "_csrf": token, "acceso": ADMIN_USER, "contrasena": ADMIN_PASS})
    comprobar(codigo == 302, "Login de administrador correcto")
    codigo, cuerpo, _ = anonimo.pedir("GET", "/app/inicio")
    comprobar(codigo == 200 and "Administrador" in cuerpo, "Sesion de administrador con rol correcto")

    estudiante = Sesion()
    codigo, cuerpo, _ = estudiante.pedir("GET", "/registro")
    comprobar(codigo == 200, "Registro de estudiante disponible")
    token = csrf(cuerpo)
    codigo, _, _ = estudiante.pedir("POST", "/registro", {
        "_csrf": token, "identificacion": "EST001", "nombre": "Estudiante Tomcat",
        "correo": STUDENT_USER, "nivel": "A1", "institucion": "",
        "contrasena": STUDENT_PASS, "confirmacion": STUDENT_PASS, "acepta": "true"})
    comprobar(codigo == 302, "Registro de estudiante por el metodo real")

    codigo, cuerpo, _ = estudiante.pedir("GET", "/login")
    token = csrf(cuerpo)
    codigo, _, _ = estudiante.pedir("POST", "/login", {
        "_csrf": token, "acceso": STUDENT_USER, "contrasena": STUDENT_PASS})
    comprobar(codigo == 302, "Login de estudiante correcto")
    codigo, cuerpo, _ = estudiante.pedir("GET", "/app/inicio")
    comprobar(codigo == 200 and "Estudiante" in cuerpo, "Sesion de estudiante con rol correcto")
    codigo, _, _ = estudiante.pedir("GET", "/app/admin/administradores")
    comprobar(codigo == 403, "Estudiante no accede a administracion")

    api = Sesion()
    codigo, _, _ = api.pedir("GET", "/api/v1/auth/token")
    comprobar(codigo == 405, "API token rechaza GET")
    codigo, _, _ = api.pedir("POST", "/api/v1/auth/token", json_texto="")
    comprobar(codigo == 400, "API token rechaza cuerpo vacio")
    codigo, _, _ = api.pedir("POST", "/api/v1/auth/token", json_texto="{")
    comprobar(codigo == 400, "API token rechaza JSON malformado")
    codigo, _, _ = api.pedir("POST", "/api/v1/auth/token", json_texto=json.dumps(
        {"usuario": ADMIN_USER, "contrasena": "incorrecta"}))
    comprobar(codigo == 401, "API token rechaza credenciales incorrectas")
    codigo, cuerpo, _ = api.pedir("POST", "/api/v1/auth/token", json_texto=json.dumps(
        {"usuario": ADMIN_USER, "contrasena": ADMIN_PASS}))
    comprobar(codigo == 200, "API emite token Bearer")
    token_api = json.loads(cuerpo)["token"]
    comprobar(bool(token_api), "API devuelve un token utilizable")

    codigo, _, _ = api.pedir("GET", "/api/v1/auth/validar")
    comprobar(codigo == 401, "API protegida sin Bearer devuelve 401")
    codigo, _, _ = api.pedir("GET", "/api/v1/auth/validar", cabeceras={"Authorization": "Bearer " + token_api})
    comprobar(codigo == 200, "API protegida con Bearer devuelve 200")
    codigo, _, _ = api.pedir("GET", "/api/v1/catalogos/grupos", cabeceras={"Authorization": "Bearer " + token_api})
    comprobar(codigo == 403, "API respeta el rol en catalogos")
    codigo, _, _ = api.pedir("POST", "/api/v1/auth/revoke", cabeceras={"Authorization": "Bearer " + token_api})
    comprobar(codigo == 204, "Revocacion de token devuelve 204")
    codigo, _, _ = api.pedir("GET", "/api/v1/auth/validar", cabeceras={"Authorization": "Bearer " + token_api})
    comprobar(codigo == 401, "Token revocado deja de valer")

    print(f"OK: {VERIFICACIONES} verificaciones HTTP/HTTPS.")


if __name__ == "__main__":
    main()
