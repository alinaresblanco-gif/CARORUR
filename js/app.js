/* ============================================================
   app.js  –  Lógica de navegación CARORUR
   ============================================================ */

(function () {
  'use strict';

  /* --- Referencias al DOM --- */
  const imagenContenedor = document.querySelector('.imagen-contenedor');
  const zonas            = document.querySelectorAll('.zona-clic');
  const contenedorVista  = document.getElementById('contenedor-vista');
  let   iframeVista      = document.getElementById('iframe-vista');
  const btnVolver        = document.getElementById('btn-volver');
  const btnEdicion       = document.getElementById('btn-edicion');
  const STORAGE_VISTA_KEY = 'carorur_vista_activa';
  const STORAGE_LAST_ACTIVE_TS = 'carorur_last_active_ts';
  const STORAGE_SKIP_RESTORE_UNTIL = 'carorur_skip_restore_until';

  /* ============================================================
     HISTORY API: estado base al cargar la app
     Así el botón "atrás" del móvil siempre tiene un estado
     al que regresar antes de salir de la app.
  ============================================================ */
  history.replaceState({ vista: null }, '');

  // Destruye el iframe actual y lo reemplaza por uno nuevo vacío.
  // Esto fuerza al WebView a liberar completamente el renderer del frame
  // y todos sus recursos (audio, blobs, DOM) sin esperar al GC.
  function resetIframe() {
    var parent = iframeVista.parentNode;
    var next   = iframeVista.nextSibling;
    var allow  = iframeVista.getAttribute('allow') || '';

    // Primero poner about:blank para disparar pagehide/unload en la vista cargada
    try { iframeVista.src = 'about:blank'; } catch (_) {}

    parent.removeChild(iframeVista);

    var nuevo = document.createElement('iframe');
    nuevo.id          = 'iframe-vista';
    nuevo.frameBorder = '0';
    if (allow) nuevo.setAttribute('allow', allow);
    parent.insertBefore(nuevo, next);
    iframeVista = nuevo;
  }

  function abrirVista(rutaVista, options) {
    const opts = options || {};
    iframeVista.src = rutaVista;
    contenedorVista.classList.remove('oculto');
    localStorage.setItem(STORAGE_VISTA_KEY, rutaVista);
    if (opts.replaceState) {
      history.replaceState({ vista: rutaVista }, '');
      return;
    }
    // Empuja un nuevo estado para que el botón atrás lo capture
    history.pushState({ vista: rutaVista }, '');
  }

  function cerrarVista() {
    resetIframe();
    contenedorVista.classList.add('oculto');
    localStorage.removeItem(STORAGE_VISTA_KEY);
    localStorage.setItem(STORAGE_SKIP_RESTORE_UNTIL, String(Date.now() + 5000));
    history.replaceState({ vista: null }, '');
  }

  // Expuesto para que vistas internas (iframe) puedan cerrar la vista sin depender del historial.
  window.carorurCloseVista = function () {
    cerrarVista();
  };

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

  // Restaurar si hubo recarga breve, salvo que
  // acabemos de cerrar la vista de forma intencional.
  const nav = performance.getEntriesByType('navigation')[0];
  const esRecarga = nav && (nav.type === 'reload' || nav.type === 'back_forward');
  const vistaGuardada = localStorage.getItem(STORAGE_VISTA_KEY);
  const estuvoActivaReciente = (Date.now() - Number(localStorage.getItem(STORAGE_LAST_ACTIVE_TS) || '0')) < 45000;
  const skipRestoreUntil = Number(localStorage.getItem(STORAGE_SKIP_RESTORE_UNTIL) || '0');
  const canRestore = Date.now() > skipRestoreUntil;
  if (vistaGuardada && canRestore && (esRecarga || estuvoActivaReciente)) {
    abrirVista(vistaGuardada);
  }

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') {
      localStorage.setItem(STORAGE_LAST_ACTIVE_TS, String(Date.now()));
    }
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

  window.addEventListener('message', function (event) {
    if (!event || event.data !== 'carorur:close-vista') return;
    cerrarVista();
  });

  /* ============================================================
     MODO EDICIÓN: muestra/oculta las zonas clicables
     Útil para ajustar posición y tamaño de cada zona
  ============================================================ */
  btnEdicion.addEventListener('click', function () {
    imagenContenedor.classList.toggle('modo-edicion');
  });

})();
