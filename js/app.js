/* ============================================================
   app.js  –  Lógica de navegación CARORUR
   ============================================================ */

(function () {
  'use strict';

  /* --- Referencias al DOM --- */
  const imagenContenedor = document.querySelector('.imagen-contenedor');
  const zonas            = document.querySelectorAll('.zona-clic');
  const contenedorVista  = document.getElementById('contenedor-vista');
  const iframeVista      = document.getElementById('iframe-vista');
  const btnVolver        = document.getElementById('btn-volver');
  const btnEdicion       = document.getElementById('btn-edicion');

  /* ============================================================
     HISTORY API: estado base al cargar la app
     Así el botón "atrás" del móvil siempre tiene un estado
     al que regresar antes de salir de la app.
  ============================================================ */
  history.replaceState({ vista: null }, '');

  function abrirVista(rutaVista) {
    iframeVista.src = rutaVista;
    contenedorVista.classList.remove('oculto');
    // Empuja un nuevo estado para que el botón atrás lo capture
    history.pushState({ vista: rutaVista }, '');
  }

  function cerrarVista() {
    iframeVista.src = '';
    contenedorVista.classList.add('oculto');
  }

  /* ============================================================
     NAVEGACIÓN: clic en una zona → abre la vista en el iframe
  ============================================================ */
  zonas.forEach(function (zona) {
    zona.addEventListener('click', function () {
      const rutaVista = zona.dataset.vista;
      if (!rutaVista) return;
      abrirVista(rutaVista);
    });
  });

  /* ============================================================
     BOTÓN VOLVER (pantalla): limpia el iframe y vuelve al inicio
  ============================================================ */
  btnVolver.addEventListener('click', function () {
    history.back(); // activa popstate → cerrarVista()
  });

  /* ============================================================
     BOTÓN ATRÁS DEL MÓVIL / NAVEGADOR (popstate)
     Si hay una vista abierta, la cierra en lugar de salir.
  ============================================================ */
  window.addEventListener('popstate', function (e) {
    if (!contenedorVista.classList.contains('oculto')) {
      cerrarVista();
    }
  });

  /* ============================================================
     MODO EDICIÓN: muestra/oculta las zonas clicables
     Útil para ajustar posición y tamaño de cada zona
  ============================================================ */
  btnEdicion.addEventListener('click', function () {
    imagenContenedor.classList.toggle('modo-edicion');
  });

})();
