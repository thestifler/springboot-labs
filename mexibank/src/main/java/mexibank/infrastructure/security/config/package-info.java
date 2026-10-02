/**
 * Cableado de seguridad: beans y reglas de acceso.
 *
 * <p><strong>Por que aqui y no con {@code @Component} en cada adaptador.</strong>
 * Los adaptadores de seguridad dependen unos de otros: el emisor necesita el
 * encoder, el hasher necesita el codificador, el filtro necesita el emisor. Con las
 * anotaciones repartidas, saber que clase recibe cada dependencia obliga a abrir
 * cinco ficheros. Aqui se lee entero de un vistazo.
 *
 * <p><strong>Por que las rutas se declaran aqui y no con anotaciones en los
 * controladores.</strong> Para que exista un unico sitio donde se lee que es publico
 * y que no. Con las anotaciones en cada metodo, contestar a la pregunta mas basica
 * de una API (que puedo llamar y quien) obliga a revisar todos los controladores.
 */
package mexibank.infrastructure.security.config;