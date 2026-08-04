// Handler for other .js files
  var tabs = document.querySelectorAll('.tab');
  var tabink = document.querySelector('.tabink');

  /* Inset to match the tab's own padding, so the bar sits under the label
     rather than the whole button. */
  function moveInk() {
    var active = document.querySelector('.tab.active');
    if (!active || !tabink) {
      return;
    }
    tabink.style.width = Math.max(0, active.offsetWidth - 28) + 'px';
    tabink.style.transform = 'translateX(' + (active.offsetLeft + 14) + 'px)';
  }

  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () {
      tabs.forEach(function (t) { t.classList.toggle('active', t === tab); });
      document.querySelectorAll('.tabpane').forEach(function (pane) {
        pane.classList.toggle('active', pane.id === tab.dataset.pane);
      });
      moveInk();
    });
  });

  // Placed without a transition on the first paint, so it does not fly in from
  // the left corner when the page opens.
  if (tabink) {
    tabink.style.transition = 'none';
    moveInk();
    requestAnimationFrame(function () { tabink.style.transition = ''; });
    window.addEventListener('resize', moveInk);
  }

  var summary = document.getElementById('summary');
  var openSummary = document.getElementById('sumopen');

  /* The donut sweeps and the bars grow, so the headline figures count up with
     them rather than being the one still thing on an animating panel. */
  function countUp(el) {
    var text = el.dataset.value || el.textContent;
    el.dataset.value = text;
    var parts = text.match(/^([^0-9.-]*)([0-9]*\.?[0-9]+)(.*)$/);
    if (!parts) {
      return;
    }
    var target = parseFloat(parts[2]);
    var places = (parts[2].split('.')[1] || '').length;
    var began = null;

    requestAnimationFrame(function frame(now) {
      began = began === null ? now : began;
      var t = Math.min(1, (now - began) / 700);
      var eased = 1 - Math.pow(1 - t, 3);
      el.textContent = parts[1] + (target * eased).toFixed(places) + parts[3];
      if (t < 1) {
        requestAnimationFrame(frame);
      }
    });
  }

  /* The donut opens up in place: it grows, its key unrolls beneath it, and the
     rest of the summary is pushed down by the reflow rather than covered. */
  var donutbtn = document.querySelector('.donutbtn');
  if (donutbtn) {
    donutbtn.addEventListener('click', function () {
      var top = donutbtn.closest('.sum-top');
      var open = top.classList.toggle('open');
      donutbtn.setAttribute('aria-expanded', open ? 'true' : 'false');
      donutbtn.title = open ? 'Click to shrink' : 'Click for a closer look';
    });
  }

  function showSummary(show) {
    if (!summary) {
      return;
    }
    if (show) {
      raise(summary);
      summary.querySelectorAll('.fig-value').forEach(countUp);
    } else {
      lower(summary);
      var top = summary.querySelector('.sum-top.open');
      if (top) {
        top.classList.remove('open');
        donutbtn.setAttribute('aria-expanded', 'false');
      }
    }
  }

  if (openSummary && summary) {
    openSummary.addEventListener('click', function () {
      showSummary(true);
    });
    document.getElementById('sclose').addEventListener('click', function () {
      showSummary(false);
    });
    summary.addEventListener('click', function (event) {
      if (event.target === summary) {
        showSummary(false);
      }
    });
  }

  overlay.addEventListener('click', function (event) {
    if (event.target === overlay) {
      close();
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') {
      return;
    }
    if (summary && summary.classList.contains('open')) {
      showSummary(false);
      return;
    }
    if (seedApi && seedApi.busy()) {
      seedApi.reset();
      return;
    }
    close();
  });

  var resizeTimer = null;
  window.addEventListener('resize', function () {
    if (!overlay.classList.contains('open')) {
      return;
    }
    if (seedApi) {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(function () {
        if (seedApi && lastCard) {
          var group = lastCard.querySelector('.cy').dataset.group;
          seedApi.destroy();
          stage.innerHTML = '<div id="ocy"></div>';
          seedApi = hydrateSeed(document.getElementById('ocy'), group);
        }
      }, 200);
      return;
    }
    fit();
  });
