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
     NAVEGACIÓN: clic en una zona → abre la vista en el iframe
  ============================================================ */
  zonas.forEach(function (zona) {
    zona.addEventListener('click', function () {
      const rutaVista = zona.dataset.vista;

      if (!rutaVista) return;

      iframeVista.src = rutaVista;
      contenedorVista.classList.remove('oculto');
    });
  });

  /* ============================================================
     BOTÓN VOLVER: limpia el iframe y vuelve a la pantalla inicio
  ============================================================ */
  btnVolver.addEventListener('click', function () {
    iframeVista.src = '';
    contenedorVista.classList.add('oculto');
  });

  /* ============================================================
     MODO EDICIÓN: muestra/oculta las zonas clicables
     Útil para ajustar posición y tamaño de cada zona
  ============================================================ */
  btnEdicion.addEventListener('click', function () {
    imagenContenedor.classList.toggle('modo-edicion');
  });

})();
