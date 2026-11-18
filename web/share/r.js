// The shared routine page (task 23, decision 98). Reads /shares/<id> from the
// same origin (CloudFront sends it to the API) and shows the routine or
// program, with a button that opens it in the app.
//
// Names in a share are typed by whoever published it, so every piece of text
// from the share goes in with textContent, never innerHTML.
"use strict";

(function () {
  var STRINGS = {
    en: {
      title: "DinoGym · shared routine",
      loading: "Loading…",
      notFound: "This link does not lead to a routine. It may be mistyped.",
      failed: "Could not load the routine. Check your connection and try again.",
      retry: "TRY AGAIN",
      sharedRoutine: "Shared routine",
      sharedProgram: "Shared program",
      gym: "Gym",
      calisthenics: "Calisthenics",
      days: function (n) { return n === 1 ? "1 day in the cycle" : n + " days in the cycle"; },
      exercises: function (n) { return n === 1 ? "1 exercise" : n + " exercises"; },
      cycle: "Cycle",
      rest: "ZZZ",
      restDay: "Rest day",
      routines: "Routines",
      noExercises: "No exercises yet.",
      reps: function (min, max) { return min === max ? String(min) : min + "–" + max; },
      restFor: "rest",
      weight: "KG",
      open: "OPEN IN DINOGYM",
      getApp: "GET THE APP",
      textbox: "* Open this in DinoGym to add it to your programs. You can change anything after.",
      notAndroid: "DinoGym is an Android app. Open this link on your Android phone to add it.",
      appNote: "Free and open source. Download the APK from GitHub Releases.",
      tooNew: "This routine was made with a newer DinoGym. Update the app to open it."
    },
    es: {
      title: "DinoGym · rutina compartida",
      loading: "Cargando…",
      notFound: "Este enlace no lleva a ninguna rutina. Puede que esté mal escrito.",
      failed: "No se pudo cargar la rutina. Revisa tu conexión e inténtalo de nuevo.",
      retry: "REINTENTAR",
      sharedRoutine: "Rutina compartida",
      sharedProgram: "Programa compartido",
      gym: "Gimnasio",
      calisthenics: "Calistenia",
      days: function (n) { return n === 1 ? "1 día en el ciclo" : n + " días en el ciclo"; },
      exercises: function (n) { return n === 1 ? "1 ejercicio" : n + " ejercicios"; },
      cycle: "Ciclo",
      rest: "ZZZ",
      restDay: "Día de descanso",
      routines: "Rutinas",
      noExercises: "Todavía no tiene ejercicios.",
      reps: function (min, max) { return min === max ? String(min) : min + "–" + max; },
      restFor: "descanso",
      weight: "KG",
      open: "ABRIR EN DINOGYM",
      getApp: "DESCARGAR LA APP",
      textbox: "* Ábrela en DinoGym para agregarla a tus programas. Después puedes cambiar lo que quieras.",
      notAndroid: "DinoGym es una app para Android. Abre este enlace en tu celular Android para agregarla.",
      appNote: "Gratis y de código abierto. Descarga el APK desde GitHub Releases.",
      tooNew: "Esta rutina se hizo con un DinoGym más nuevo. Actualiza la app para abrirla."
    }
  };
  var lang = /^es\b/i.test(navigator.language || "") ? "es" : "en";
  var t = STRINGS[lang];
  document.documentElement.lang = lang;
  document.title = t.title;

  var root = document.documentElement;
  var app = document.getElementById("app");
  var status = document.getElementById("status");
  var match = /^\/r\/([A-Za-z0-9_-]{22})\/?$/.exec(location.pathname);
  var id = match ? match[1] : null;

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function clock(seconds) {
    var m = Math.floor(seconds / 60), s = seconds % 60;
    return m + ":" + (s < 10 ? "0" : "") + s;
  }

  function showStatus(text, isError, retry) {
    status.textContent = text;
    status.className = "status" + (isError ? " error" : "");
    var old = document.getElementById("retry");
    if (old) old.remove();
    if (retry) {
      var b = el("button", "btn secondary", t.retry);
      b.id = "retry";
      b.type = "button";
      b.addEventListener("click", load);
      app.appendChild(b);
    }
  }

  // An exercise line: "Pull-up", then "3 × 5–8 · rest 2:30". A pattern
  // exercise shows its pattern with the reps in place of {n}.
  function exerciseItem(item) {
    var li = el("li");
    var name = el("div", "ex-name", item.exercise.name);
    if (item.exercise.weightEnabled) name.appendChild(el("span", "tag", t.weight));
    li.appendChild(name);
    var reps = t.reps(item.repMin, item.repMax);
    var detail;
    if (item.exercise.kind === "SHOW_ONLY") {
      detail = item.exercise.template || "";
    } else {
      var what = item.exercise.kind === "TEMPLATE" && item.exercise.template
        ? item.exercise.template.split("{n}").join(reps)
        : reps;
      detail = item.sets + " × " + what;
      if (item.restSeconds > 0) detail += " · " + t.restFor + " " + clock(item.restSeconds);
    }
    if (detail) li.appendChild(el("div", "ex-detail", detail));
    return li;
  }

  function routineCard(r) {
    var card = el("section", "card");
    card.appendChild(el("h2", null, r.name));
    if (r.exercises.length === 0) {
      card.appendChild(el("p", "note", t.noExercises));
      return card;
    }
    var list = el("ol");
    r.exercises.forEach(function (item) { list.appendChild(exerciseItem(item)); });
    card.appendChild(list);
    return card;
  }

  function actions() {
    var box = el("div", "actions");
    var releases = root.dataset.releases;
    if (/Android/i.test(navigator.userAgent)) {
      // An intent link opens the app even when Android has not verified the
      // App Link; without the app, Chrome goes to the fallback (the APK).
      var open = el("a", "btn primary", t.open);
      open.href = "intent://" + location.host + location.pathname +
        "#Intent;scheme=https;package=" + root.dataset.package +
        ";S.browser_fallback_url=" + encodeURIComponent(releases) + ";end";
      box.appendChild(open);
    } else {
      box.appendChild(el("p", "note", t.notAndroid));
    }
    var get = el("a", "btn secondary", t.getApp);
    get.href = releases;
    get.rel = "noopener";
    box.appendChild(get);
    box.appendChild(el("p", "note", t.appNote));
    return box;
  }

  function render(share) {
    var s = share.snapshot;
    status.remove();
    var head = el("div");
    var isProgram = s.kind === "program" && s.program;
    head.appendChild(el("p", "label", isProgram ? t.sharedProgram : t.sharedRoutine));
    head.appendChild(el("h1", null, isProgram ? s.program.name : s.routines[0].name));
    if (isProgram) {
      head.appendChild(el("p", "meta", (s.program.type === "GYM" ? t.gym : t.calisthenics) + " · " + t.days(s.program.cycle.length)));
    } else {
      head.appendChild(el("p", "meta", t.exercises(s.routines[0].exercises.length)));
    }
    app.appendChild(head);
    app.appendChild(el("p", "textbox", t.textbox));
    app.appendChild(actions());

    if (isProgram) {
      app.appendChild(el("p", "label", t.cycle));
      var strip = el("ol", "cycle");
      s.program.cycle.forEach(function (step) {
        var r = typeof step.routine === "number" ? s.routines[step.routine] : null;
        var li = el("li", r ? null : "rest", r ? r.name : t.rest);
        li.title = r ? r.name : t.restDay;
        strip.appendChild(li);
      });
      app.appendChild(strip);
      app.appendChild(el("p", "label", t.routines));
    }
    s.routines.forEach(function (r) { app.appendChild(routineCard(r)); });
  }

  function load() {
    if (!id) { showStatus(t.notFound, true, false); return; }
    showStatus(t.loading, false, false);
    fetch("/shares/" + id, { headers: { Accept: "application/json" } })
      .then(function (res) {
        if (res.status === 404) { showStatus(t.notFound, true, false); return null; }
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.json();
      })
      .then(function (share) {
        if (!share) return;
        if (share.snapshot.version !== 1) { showStatus(t.tooNew, true, false); return; }
        render(share);
      })
      .catch(function () { showStatus(t.failed, true, true); });
  }

  load();
})();
