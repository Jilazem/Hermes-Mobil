/*
 * KAFES DÖVÜŞÜ — three.js r147 çizer (Tur-20, yerel asset, MIT).
 *
 * Simülasyon cage-engine.js'de (saf, deterministik); bu dosya yalnız ÇIZER:
 * ring + kafes telleri, tribün (alkışlayan izleyici-botlar), iki robot dövüşcü
 * (arena3d sembol dili: kutu gövde + vizör), hasar barları / kombo / konfeti.
 *
 * Gömme (Android WebView):
 *   window.cage.setData(jsonString)   // Kotlin köprüsü — {phase, theme, figures}
 *   window.cage.setActive(bool) / getState() / ping()
 *   Olaylar: __ArenaBridge.onSceneEvent(type, detail)
 *     ready | boot | entered | hit | whiff | block | victory | confetti |
 *     data | error | nowebgl
 *
 * Kanıt parametreleri (yalnız hata ayıklama):
 *   ?advance=N  N sn sabit-adım (1/60) simüle et   ?freeze  tek kare modu
 *   ?demo=1     geçici GÖREV AKIŞI senaryosu (intro → vuruş → zafer), CDP kanıtı
 *   ?seed=NNN   engine seed
 */
(function () {
  'use strict';

  var bridge = window.__ArenaBridge || null;
  var errors = [];
  window.addEventListener('error', function (e) { errors.push(String((e && e.message) || e)); });

  function emit(type, detail) {
    var d = (detail == null) ? '' : (typeof detail === 'string' ? detail : safeJson(detail));
    try { if (bridge && bridge.onSceneEvent) bridge.onSceneEvent(type, d); } catch (e) {}
    try { console.log('cage ' + type + ' ' + d); } catch (e2) {}
  }
  function safeJson(o) { try { return JSON.stringify(o); } catch (e) { return String(o); } }
  function el_(id) { return document.getElementById(id); }
  function showFallback(msg) {
    var f = el_('fallback');
    if (f) { f.style.display = 'flex'; if (msg) f.textContent = msg; }
  }
  function hasWebGL() {
    try {
      var c = document.createElement('canvas');
      return !!(window.WebGLRenderingContext && (c.getContext('webgl') || c.getContext('experimental-webgl')));
    } catch (e) { return false; }
  }
  function detectSoftGL() {
    try {
      var c = document.createElement('canvas');
      var g = c.getContext('webgl') || c.getContext('experimental-webgl');
      if (!g) return false;
      var dbg = g.getExtension('WEBGL_debug_renderer_info');
      var name = dbg ? String(g.getParameter(dbg.UNMASKED_RENDERER_WEBGL)) : '';
      var lose = g.getExtension('WEBGL_lose_context');
      if (lose) lose.loseContext();
      return /swiftshader|llvmpipe|softpipe|software/i.test(name);
    } catch (e) { return false; }
  }
  function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
  function lcg(seed) { var s = seed >>> 0; return function () { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; }; }
  function qnum(name, d) {
    var m = new RegExp('[?&]' + name + '=([^&]*)').exec(location.search);
    var v = parseFloat(m ? decodeURIComponent(m[1]) : NaN);
    return isNaN(v) ? d : v;
  }
  function qflag(name) { return new RegExp('[?&]' + name + '(=|&|$)').test(location.search); }

  var Q = { advance: qnum('advance', 0), freeze: qflag('freeze'), demo: qflag('demo'), seed: qnum('seed', 0xC0FFEE) };

  if (typeof THREE === 'undefined' || typeof window.CageEngine === 'undefined') {
    showFallback('motor yuklenemedi'); emit('error', 'motor-yok'); return;
  }
  if (!hasWebGL()) { showFallback('3D sahne bu cihazda calismiyor'); emit('nowebgl', 'webgl-yok'); return; }
  var SOFT_GL = detectSoftGL();
  var WANT_BUFFER = SOFT_GL || Q.advance > 0 || Q.freeze;

  /* ── palet (arena3d ile aynı aile) ─────────────────────────────────────── */
  var P = {
    bg: 0x0b0f13, floor: 0x141b22, canvas: 0x22303c,
    accent: 0x5ec8ff, ok: 0x4ade80, danger: 0xff4d5e, gold: 0xffb347,
    rope: 0x7fb4d8, post: 0x36445a,
    bodyB: 0x36445a, trimB: 0x53657a,
    visorA: 0x8ef0ff, visorB: 0xffb347,
    crowd: [0x2b3a4a, 0x33465a, 0x27404f, 0x3b4d63],
    confetti: [0x5ec8ff, 0xffb347, 0x4ade80, 0xff4d5e, 0xc58cff, 0xffe98a],
  };
  function srgb(hex) { return new THREE.Color(hex).convertSRGBToLinear(); }

  /* ── renderer / sahne ──────────────────────────────────────────────────── */
  var STAGE = el_('stage');
  var renderer = new THREE.WebGLRenderer({
    antialias: !SOFT_GL, preserveDrawingBuffer: WANT_BUFFER, powerPreference: 'high-performance'
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputEncoding = THREE.sRGBEncoding;
  renderer.setClearColor(new THREE.Color(P.bg), 1);
  STAGE.appendChild(renderer.domElement);

  var scene = new THREE.Scene();
  scene.background = new THREE.Color(P.bg);
  scene.fog = new THREE.Fog(srgb(P.bg), 16, 42);

  var camera = new THREE.PerspectiveCamera(46, 1, 0.1, 200);
  camera.position.set(0, 3.4, 11.5);
  camera.lookAt(0, 1.3, 0);
  scene.add(camera);

  scene.add(new THREE.HemisphereLight(srgb(0x9fb9cf), srgb(0x0b0f13), 0.85));
  var key = new THREE.DirectionalLight(srgb(0xffffff), 1.0);
  key.position.set(3, 8, 6); scene.add(key);
  var rimL = new THREE.PointLight(srgb(P.accent), 1.2, 16, 2); rimL.position.set(-5, 3, 2); scene.add(rimL);
  var rimR = new THREE.PointLight(srgb(P.gold), 1.0, 16, 2); rimR.position.set(5, 3, 2); scene.add(rimR);
  var spot = new THREE.SpotLight(srgb(0xffffff), 1.4, 26, 0.55, 0.5, 1.2);
  spot.position.set(0, 10, 2); spot.target.position.set(0, 0, 0);
  scene.add(spot); scene.add(spot.target);

  /* zemin + ring */
  var floor = new THREE.Mesh(
    new THREE.PlaneGeometry(60, 40),
    new THREE.MeshStandardMaterial({ color: srgb(P.floor), roughness: 0.95 })
  );
  floor.rotation.x = -Math.PI / 2; floor.position.y = -0.02;
  scene.add(floor);

  var RH = 3.4; // ring halı yarı-genişliği
  var canvasMesh = new THREE.Mesh(
    new THREE.BoxGeometry(RH * 2, 0.18, RH * 2),
    new THREE.MeshStandardMaterial({ color: srgb(P.canvas), roughness: 0.8 })
  );
  canvasMesh.position.y = 0.09;
  scene.add(canvasMesh);

  // ring çizgileri (kenar)
  var edgeMat = new THREE.MeshBasicMaterial({ color: srgb(P.accent), transparent: true, opacity: 0.7 });
  var edgeGeo = new THREE.BoxGeometry(RH * 2 + 0.06, 0.02, 0.06);
  [-RH, RH].forEach(function (z) {
    var m = new THREE.Mesh(edgeGeo, edgeMat); m.position.set(0, 0.19, z); scene.add(m);
  });
  [-RH, RH].forEach(function (x) {
    var m = new THREE.Mesh(edgeGeo, edgeMat);
    m.rotation.y = Math.PI / 2; m.position.set(x, 0.19, 0); scene.add(m);
  });

  /* kafes: 4 direk + üç sıra tel + köşe ışıkları */
  var postMat = new THREE.MeshStandardMaterial({ color: srgb(P.post), metalness: 0.6, roughness: 0.4 });
  var ropeMat = new THREE.MeshBasicMaterial({ color: srgb(P.rope), transparent: true, opacity: 0.5 });
  var cornerGlow = [];
  [[-RH, -RH], [RH, -RH], [-RH, RH], [RH, RH]].forEach(function (p, i) {
    var post = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.09, 2.6, 8), postMat);
    post.position.set(p[0], 1.3, p[1]);
    scene.add(post);
    var bulb = new THREE.Mesh(
      new THREE.SphereGeometry(0.09, 8, 8),
      new THREE.MeshBasicMaterial({ color: srgb(i % 2 ? P.accent : P.gold) })
    );
    bulb.position.set(p[0], 2.66, p[1]);
    scene.add(bulb);
    cornerGlow.push(bulb);
  });
  [0.9, 1.5, 2.1].forEach(function (y) {
    // X duvarları (x sabit, z boyunca) ve Z duvarları (z sabit, x boyunca)
    [-RH, RH].forEach(function (x) {
      var r = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.018, RH * 2, 4), ropeMat);
      r.rotation.x = Math.PI / 2;
      r.position.set(x, y, 0);
      scene.add(r);
    });
    [-RH, RH].forEach(function (z) {
      var r = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.018, RH * 2, 4), ropeMat);
      r.rotation.z = Math.PI / 2;
      r.position.set(0, y, z);
      scene.add(r);
    });
  });

  /* tribün: 3 sıra izleyici-bot, alkış animasyonlu */
  var crowdRows = [];
  var crowdRng = lcg(1234);
  for (var row = 0; row < 3; row++) {
    var n = 9 - row;
    var rowG = new THREE.Group();
    rowG.position.set(0, 0.18 + row * 0.5, -(RH + 1.1 + row * 1.05));
    for (var i = 0; i < n; i++) {
      var g = new THREE.Group();
      var x = (i - (n - 1) / 2) * 0.72 + (crowdRng() - 0.5) * 0.2;
      g.position.set(x, 0, 0);
      var c = srgb(P.crowd[Math.floor(crowdRng() * P.crowd.length)]);
      var body = new THREE.Mesh(
        new THREE.BoxGeometry(0.4, 0.55, 0.3),
        new THREE.MeshStandardMaterial({ color: c, roughness: 0.8 })
      );
      body.position.y = 0.45;
      g.add(body);
      var head = new THREE.Mesh(
        new THREE.BoxGeometry(0.26, 0.24, 0.24),
        new THREE.MeshStandardMaterial({ color: c.clone().multiplyScalar(1.25), roughness: 0.8 })
      );
      head.position.y = 0.86;
      g.add(head);
      var cVisor = new THREE.Mesh(
        new THREE.BoxGeometry(0.2, 0.05, 0.02),
        new THREE.MeshBasicMaterial({ color: srgb(0x9fe8ff), transparent: true, opacity: 0.85 })
      );
      cVisor.position.set(0, 0.87, 0.13);
      g.add(cVisor);
      // kollar (alkış için)
      var armL = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.34, 0.12),
        new THREE.MeshStandardMaterial({ color: c, roughness: 0.8 }));
      armL.position.set(-0.27, 0.5, 0.06);
      var armR = armL.clone(); armR.position.x = 0.27;
      g.add(armL); g.add(armR);
      rowG.add(g);
      crowdRows.push({ g: g, armL: armL, armR: armR, baseY: 0, phase: crowdRng() * 6.28, row: row });
    }
    scene.add(rowG);
  }

  /* ── dövüşcü robot (arena3d sembol dili) ───────────────────────────────── */
  function makeFighterMesh(accentHex, visorHex) {
    var grp = new THREE.Group();
    var bodyMat = new THREE.MeshStandardMaterial({ color: srgb(0x36445a), metalness: 0.55, roughness: 0.42, emissive: srgb(0x0d2130), emissiveIntensity: 0.7 });
    var trimMat = new THREE.MeshStandardMaterial({ color: srgb(0x53657a), metalness: 0.7, roughness: 0.35 });
    var accMat = new THREE.MeshStandardMaterial({ color: srgb(accentHex), metalness: 0.35, roughness: 0.4, emissive: srgb(accentHex), emissiveIntensity: 0.55 });
    var visorMat = new THREE.MeshStandardMaterial({ color: srgb(visorHex), emissive: srgb(visorHex), emissiveIntensity: 1.1 });
    var coreMat = new THREE.MeshStandardMaterial({ color: srgb(0xdcf6ff), emissive: srgb(visorHex), emissiveIntensity: 1.2, metalness: 0.2, roughness: 0.3 });

    function part(parent, geo, mat, x, y, z) {
      var m = new THREE.Mesh(geo, mat); m.position.set(x, y, z); parent.add(m); return m;
    }
    // gövde
    part(grp, new THREE.BoxGeometry(0.88, 0.6, 0.5), bodyMat, 0, 1.3, 0);
    part(grp, new THREE.BoxGeometry(0.62, 0.44, 0.42), bodyMat, 0, 0.88, 0);
    part(grp, new THREE.BoxGeometry(0.8, 0.26, 0.46), trimMat, 0, 0.58, 0);
    part(grp, new THREE.BoxGeometry(0.98, 0.18, 0.42), trimMat, 0, 1.68, 0);
    part(grp, new THREE.SphereGeometry(0.1, 10, 8), coreMat, 0, 1.34, 0.28);
    // baş
    part(grp, new THREE.BoxGeometry(0.48, 0.44, 0.44), bodyMat, 0, 1.98, 0);
    part(grp, new THREE.BoxGeometry(0.4, 0.12, 0.06), visorMat, 0, 2.0, 0.23);
    part(grp, new THREE.BoxGeometry(0.08, 0.26, 0.36), accMat, 0, 2.25, -0.03);
    // bacaklar (yürüme için ayrı pivotlar)
    function leg(side) {
      var l = new THREE.Group();
      l.position.set(side * 0.21, 0.58, 0);
      part(l, new THREE.BoxGeometry(0.2, 0.5, 0.24), trimMat, 0, -0.28, 0);
      part(l, new THREE.BoxGeometry(0.26, 0.12, 0.32), bodyMat, 0, -0.55, 0.03);
      grp.add(l); return l;
    }
    var legL = leg(-1), legR = leg(1);
    // kollar: omuz pivotu → ileri yumruk animasyonu
    function arm(side) {
      var a = new THREE.Group();
      a.position.set(side * 0.62, 1.55, 0);
      part(a, new THREE.BoxGeometry(0.2, 0.46, 0.22), trimMat, 0, -0.24, 0);
      part(a, new THREE.BoxGeometry(0.17, 0.34, 0.18), bodyMat, 0, -0.6, 0.06);
      part(a, new THREE.BoxGeometry(0.22, 0.16, 0.22), accMat, 0, -0.82, 0.12);
      grp.add(a); return a;
    }
    var armL = arm(-1), armR = arm(1);
    // enerji kalkanı (blok anında parlar)
    var shield = new THREE.Mesh(
      new THREE.CircleGeometry(0.55, 24),
      new THREE.MeshBasicMaterial({ color: srgb(accentHex), transparent: true, opacity: 0, side: THREE.DoubleSide })
    );
    shield.position.set(0, 1.3, 0.42);
    grp.add(shield);

    return { grp: grp, bodyMat: bodyMat, armL: armL, armR: armR, legL: legL, legR: legR, shield: shield };
  }

  var rigA = makeFighterMesh(0x5ec8ff, 0x8ef0ff);
  var rigB = makeFighterMesh(0xffb347, 0xffb347);
  scene.add(rigA.grp); scene.add(rigB.grp);

  /* isabet kıvılcımı havuzu */
  var SPARK_N = 40;
  var sparkPos = new Float32Array(SPARK_N * 3);
  var sparkGeo = new THREE.BufferGeometry();
  sparkGeo.setAttribute('position', new THREE.BufferAttribute(sparkPos, 3));
  var sparks = new THREE.Points(sparkGeo, new THREE.PointsMaterial({
    color: srgb(0xffd76f), size: 0.14, transparent: true, opacity: 0.95,
    blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true
  }));
  sparks.frustumCulled = false;
  scene.add(sparks);
  var sparkState = [];
  for (var sp = 0; sp < SPARK_N; sp++) sparkState.push({ x: 0, y: -99, z: 0, vx: 0, vy: 0, vz: 0, life: 0 });
  var sparkRng = lcg(7);
  function burstSpark(x, y, count) {
    var n = 0;
    for (var i = 0; i < SPARK_N && n < count; i++) {
      var s = sparkState[i];
      if (s.life > 0) continue;
      s.x = x + (sparkRng() - 0.5) * 0.2; s.y = y; s.z = (sparkRng() - 0.5) * 0.2;
      s.vx = (sparkRng() - 0.5) * 3.2; s.vy = 1.2 + sparkRng() * 2.4; s.vz = (sparkRng() - 0.5) * 2.2;
      s.life = 0.45 + sparkRng() * 0.25;
      n++;
    }
  }
  function updateSparks(dt) {
    var any = false;
    for (var i = 0; i < SPARK_N; i++) {
      var s = sparkState[i];
      if (s.life > 0) {
        s.life -= dt;
        s.vy -= 7 * dt;
        s.x += s.vx * dt; s.y += s.vy * dt; s.z += s.vz * dt;
        if (s.y < 0.22) { s.y = 0.22; s.vy *= -0.4; }
        any = true;
      }
      sparkPos[i * 3] = s.life > 0 ? s.x : 0;
      sparkPos[i * 3 + 1] = s.life > 0 ? s.y : -99;
      sparkPos[i * 3 + 2] = s.life > 0 ? s.z : 0;
    }
    sparkGeo.attributes.position.needsUpdate = true;
    sparks.visible = any;
  }

  /* konfeti (zafer) */
  var CONF_N = 90;
  var confGeo = new THREE.BufferGeometry();
  var confPos = new Float32Array(CONF_N * 3);
  var confCol = new Float32Array(CONF_N * 3);
  confGeo.setAttribute('position', new THREE.BufferAttribute(confPos, 3));
  confGeo.setAttribute('color', new THREE.BufferAttribute(confCol, 3));
  var confetti = new THREE.Points(confGeo, new THREE.PointsMaterial({
    size: 0.1, vertexColors: true, transparent: true, opacity: 1, depthWrite: false, sizeAttenuation: true
  }));
  confetti.frustumCulled = false;
  confetti.visible = false;
  scene.add(confetti);
  var confState = [];
  var confRng = lcg(99);
  function spawnConfetti(cx) {
    confState.length = 0;
    for (var i = 0; i < CONF_N; i++) {
      var c = new THREE.Color(srgb(P.confetti[Math.floor(confRng() * P.confetti.length)]));
      confCol[i * 3] = c.r; confCol[i * 3 + 1] = c.g; confCol[i * 3 + 2] = c.b;
      confState.push({
        x: cx + (confRng() - 0.5) * 4, y: 4.5 + confRng() * 2.5, z: (confRng() - 0.5) * 2.4,
        vy: -1.2 - confRng() * 1.6, sway: confRng() * 6.28,
      });
    }
    confGeo.attributes.color.needsUpdate = true;
    confetti.visible = true;
  }
  function updateConfetti(dt, t) {
    if (!confetti.visible) return;
    var alive = 0;
    for (var i = 0; i < CONF_N; i++) {
      var s = confState[i];
      s.y += s.vy * dt;
      s.x += Math.sin(t * 2.2 + s.sway) * dt * 0.6;
      if (s.y < 0.24) { s.y = 4.6 + confRng() * 2; } // zafer sürdükçe yağmaya devam
      alive++;
      confPos[i * 3] = s.x; confPos[i * 3 + 1] = s.y; confPos[i * 3 + 2] = s.z;
    }
    confGeo.attributes.position.needsUpdate = true;
    confetti.visible = alive > 0;
  }

  /* ── HUD (DOM): isim + hasar barı + kombo ──────────────────────────────── */
  var hud = document.createElement('div');
  hud.id = 'hud';
  hud.style.cssText = 'position:fixed;top:0;left:0;right:0;padding:6px 8px;pointer-events:none;font:600 11px -apple-system,"Roboto",sans-serif;color:#dfe9f2;';
  hud.innerHTML =
    '<div style="display:flex;gap:8px;align-items:flex-start">' +
    '<div style="flex:1;min-width:0">' +
    '<div id="nL" style="max-width:46%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">—</div>' +
    '<div style="height:9px;border:1px solid #3b4d63;border-radius:2px;margin-top:2px;background:#0e151c">' +
    '<div id="hpL" style="height:100%;width:100%;background:linear-gradient(90deg,#4ade80,#2fae5f);border-radius:2px"></div></div>' +
    '</div>' +
    '<div style="flex:0 0 auto;font-weight:800;color:#ffd76f;font-size:13px;padding-top:2px">VS</div>' +
    '<div style="flex:1;min-width:0;text-align:right">' +
    '<div id="nR" style="max-width:46%;margin-left:auto;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">—</div>' +
    '<div style="height:9px;border:1px solid #3b4d63;border-radius:2px;margin-top:2px;background:#0e151c">' +
    '<div id="hpR" style="height:100%;width:100%;background:linear-gradient(270deg,#4ade80,#2fae5f);border-radius:2px;margin-left:auto"></div></div>' +
    '</div></div>' +
    '<div id="comboL" style="position:fixed;top:44px;left:12px;color:#5ec8ff;font-weight:800;font-size:15px;opacity:0"></div>' +
    '<div id="comboR" style="position:fixed;top:44px;right:12px;color:#ffb347;font-weight:800;font-size:15px;opacity:0"></div>';
  document.body.appendChild(hud);
  var H = {
    nL: el_('nL'), nR: el_('nR'), hpL: el_('hpL'), hpR: el_('hpR'),
    comboL: el_('comboL'), comboR: el_('comboR'),
  };
  function updateHud(snap) {
    var l = snap.fighters[0], r = snap.fighters[1];
    if (!l || !r) { hud.style.display = 'none'; return; }
    hud.style.display = 'block';
    if (H.nL.textContent !== l.name) H.nL.textContent = l.name || '—';
    if (H.nR.textContent !== r.name) H.nR.textContent = r.name || '—';
    var bl = l.hp / 100, br = r.hp / 100;
    H.hpL.style.width = (bl * 100).toFixed(1) + '%';
    H.hpR.style.width = (br * 100).toFixed(1) + '%';
    function lowColor(el, b) {
      el.style.background = b < 0.25 ? 'linear-gradient(90deg,#ff4d5e,#c73240)' :
        (b < 0.5 ? 'linear-gradient(90deg,#ffb347,#d18a26)' : 'linear-gradient(90deg,#4ade80,#2fae5f)');
    }
    lowColor(H.hpL, bl); lowColor(H.hpR, br);
    // kombo baloncukları
    function combo(el, f) {
      if (f.combo >= 2 && f.comboT) {
        el.textContent = f.combo + 'x KOMBO';
        el.style.opacity = String(clamp(0.35 + 1.2 * (1 - 0), 0, 1));
        var scale = 1 + 0.12 * Math.sin(perfNow() * 0.02);
        el.style.transform = 'scale(' + scale.toFixed(3) + ')';
      } else {
        el.style.opacity = '0';
      }
    }
    combo(H.comboL, l);
    combo(H.comboR, r);
  }
  function perfNow() { return (window.performance && performance.now) ? performance.now() : Date.now(); }

  /* ── dövüşcü görsel güncelleme (snapshot → rig) ────────────────────────── */
  function poseFighter(f, rig, t) {
    // modelin önü +z (vizör). Rakibe bakış: side +1 → +x'e (rot.y=-90°), -1 → -x'e.
    rig.grp.position.x = f.x;
    rig.grp.rotation.y = -f.side * Math.PI / 2;

    var bob = Math.sin(t * 2.6 + f.bobSeed * 6.28) * 0.02;
    var breathe = Math.sin(t * 1.7 + f.bobSeed * 3.1) * 0.5 + 0.5;

    // nefes/zıplama taban
    rig.grp.position.y = 0;
    rig.grp.rotation.z = 0;
    rig.grp.rotation.x = 0;
    rig.armL.rotation.x = -0.15 - breathe * 0.12;
    rig.armR.rotation.x = -0.15 + breathe * 0.12;
    rig.armL.rotation.z = 0; rig.armR.rotation.z = 0;
    rig.legL.rotation.x = 0; rig.legR.rotation.x = 0;

    switch (f.pose) {
      case 'enter':
        // yürüyüş: bacaklar salınım, kollar ritmik
        rig.legL.rotation.x = Math.sin(t * 9 + f.bobSeed) * 0.6;
        rig.legR.rotation.x = -Math.sin(t * 9 + f.bobSeed) * 0.6;
        rig.armL.rotation.x = -0.3 - Math.abs(Math.sin(t * 9 + f.bobSeed)) * 0.5;
        rig.armR.rotation.x = -0.3 - Math.abs(Math.cos(t * 9 + f.bobSeed)) * 0.5;
        break;
      case 'jab':
        // sağ kol hızlı ileri (pivot x eks. -90°)
        rig.armR.rotation.x = -1.55 * Math.min(1, f.poseT * 14);
        rig.grp.rotation.z = 0;
        break;
      case 'hook':
        // geniş yay: dirsek açık, gövde döner
        rig.armR.rotation.x = -1.35 * Math.min(1, f.poseT * 9);
        rig.armR.rotation.z = 0.6 * Math.min(1, f.poseT * 9);
        break;
      case 'stagger':
        rig.grp.rotation.z = Math.sin(f.poseT * 22 + f.bobSeed * 6) * 0.16;
        rig.grp.rotation.x = 0.10;
        rig.armL.rotation.x = 0.5; rig.armR.rotation.x = 0.5;
        break;
      case 'victory':
        rig.armL.rotation.x = -2.6; rig.armR.rotation.x = -2.6;
        rig.grp.position.y = f.victoryHop || 0;
        break;
      case 'down':
        rig.grp.rotation.x = -1.32;
        rig.grp.position.y = -0.28;
        break;
      default:
        // idle: hafif sallanma + dans (5sn hareket kuralı)
        rig.grp.position.y = bob;
        rig.armL.rotation.x = -0.25 - breathe * 0.25;
        rig.armR.rotation.x = -0.25 + breathe * 0.25;
    }

    // gard (blok pozisyonu): kollar öne
    if (f.guard > 0 && (f.pose === 'idle' || f.pose === 'engage')) {
      rig.armL.rotation.x = -1.25; rig.armR.rotation.x = -1.25;
      rig.shield.material.opacity = 0.25 + 0.1 * Math.sin(t * 8);
    } else {
      rig.shield.material.opacity = Math.max(0, rig.shield.material.opacity - 0.06);
    }

    // hasar flaşı (beyaz parıltı) + sarsıntı
    var flash = clamp(f.hitFlash, 0, 1);
    rig.bodyMat.emissiveIntensity = 0.7 + flash * 3.2;
    if (f.shakeT > 0) {
      rig.grp.position.x += Math.sin(t * 44) * 0.05 * f.shakeT;
    }
  }

  /* ── motor + döngü ─────────────────────────────────────────────────────── */
  var engine = window.CageEngine.createEngine(Q.seed);
  var lastData = null;

  function pumpEvents(snap) {
    var evs = engine.drainEvents();
    for (var i = 0; i < evs.length; i++) {
      var e = evs[i];
      emit(e.type, safeJson(e));
      if (e.type === 'hit') {
        var target = null;
        for (var k = 0; k < snap.fighters.length; k++) if (snap.fighters[k].id === e.target) target = snap.fighters[k];
        burstSpark(target ? target.x : 0, 1.35, 8 + Math.min(8, (e.combo || 1) * 2));
      } else if (e.type === 'block') {
        for (var k2 = 0; k2 < snap.fighters.length; k2++) {
          if (snap.fighters[k2].id === e.id) {
            (k2 === 0 ? rigA : rigB).shield.material.opacity = 0.85;
          }
        }
      } else if (e.type === 'confetti') {
        spawnConfetti(0);
      }
    }
  }

  /* TRIBÜN: crowdPulse ile alkış hızı artar (vuruş dalgası) */
  function updateCrowd(t, pulse) {
    for (var i = 0; i < crowdRows.length; i++) {
      var c = crowdRows[i];
      var speed = 3.2 + pulse * 6.5;
      var ph = t * speed + c.phase;
      var clap = Math.max(0, Math.sin(ph));
      c.armL.rotation.x = -0.6 - clap * 0.9;
      c.armL.rotation.z = 0.25 - clap * 0.25;
      c.armR.rotation.x = -0.6 - clap * 0.9;
      c.armR.rotation.z = -0.25 + clap * 0.25;
      // dalga: alkış yoğunluğunda zıplama
      c.g.position.y = Math.abs(Math.sin(ph)) * 0.06 * pulse;
    }
  }

  /* kamera: aksiyonu takip et (iki dövüşcünün ortası), hasarda sarsıntı */
  var camShake = 0;
  function updateCamera(snap, dt) {
    var a = snap.fighters[0], b = snap.fighters[1];
    var mid = (a && b) ? (a.x + b.x) / 2 : 0;
    var span = (a && b) ? Math.abs(a.x - b.x) : 4;
    var z = clamp(9.6 + span * 0.9, 9.6, 12.6);
    camera.position.x += (mid * 0.7 - camera.position.x) * Math.min(1, dt * 2.4);
    camera.position.z += (z - camera.position.z) * Math.min(1, dt * 2.2);
    camera.position.y = 3.3 + Math.sin(snap.t * 0.31) * 0.14 + (camShake > 0 ? (Math.random() - 0.5) * camShake * 0.5 : 0);
    camShake = Math.max(0, camShake - dt * 2.4);
    camera.lookAt(camera.position.x * 0.5, 1.3, 0);
  }

  /* ── veri köprüsü (Kotlin → JS) ────────────────────────────────────────── */
  function applyData(raw) {
    try {
      var d = (typeof raw === 'string') ? JSON.parse(raw) : (raw || {});
      lastData = d;
      engine.setData({ phase: d.phase, figures: d.figures });
      emit('data', ((d.figures && d.figures.length) || 0) + '|' + (d.phase || 'idle'));
    } catch (e) {
      emit('error', 'veri:' + (e && e.message ? e.message : 'okunamadi'));
    }
  }

  /* demo akışı (CDP kanıtı — sabit, deterministik): intro → kavga → zafer */
  function demoDataAt(sec) {
    var mk = function (sa, sb) {
      return {
        phase: 'running',
        figures: [
          { id: 'coder#r1', name: 'coder', state: sa, badge: 'Tur 1' },
          { id: 'android#r1', name: 'android', state: sb, badge: 'Tur 1' },
        ],
      };
    };
    if (sec < 2) return { phase: 'idle', figures: [] };
    if (sec < 70) return mk('working', 'working');
    return {
      phase: 'done',
      figures: [
        { id: 'coder#r1', name: 'coder', state: 'done', badge: 'Tur 1' },
        { id: 'android#r1', name: 'android', state: 'waiting', badge: 'Tur 1' },
      ],
    };
  }
  var demoClock = 0, demoPhaseApplied = '';
  function demoStep(dt) {
    if (!Q.demo) return;
    demoClock += dt;
    var d = demoDataAt(demoClock);
    var key = d.phase + '|' + (d.figures[0] ? d.figures[0].state : '');
    if (key !== demoPhaseApplied) {
      demoPhaseApplied = key;
      applyData(d);
    }
  }

  window.cage = {
    version: 'cage-1',
    setData: function (raw) { applyData(raw); },
    setActive: function (v) { var on = !!v; if (on === active) return; active = on; if (active) { renderOnce = true; start(); } else { stopLoop(); render(); } },
    isActive: function () { return active; },
    getState: function () {
      var s = engine.snapshot();
      return {
        durum: s.phase, t: +s.t.toFixed(2), kare: frames,
        savas: s.fighters.map(function (f) { return { id: f.id, hp: f.hp, poz: f.pose, kombo: f.combo, x: +f.x.toFixed(2) }; }),
        kazanan: s.winnerId, fps: +fpsVal.toFixed(1),
        cizim: renderer.info.render.calls, ucgen: renderer.info.render.triangles,
        hatalar: errors.slice(0, 3),
      };
    },
    _advance: function (secs) {
      var n = Math.round(secs * 60);
      for (var i = 0; i < n; i++) { engine.tick(1 / 60); demoStep(1 / 60); pumpEvents(engine.snapshot()); }
    },
    ping: function () { return 'pong'; },
  };

  /* ── döngü ─────────────────────────────────────────────────────────────── */
  var active = true, rafId = 0, lastT = 0, renderOnce = true, announced = false;
  var frames = 0, fpsVal = 0, fpsAcc = 0, fpsN = 0;
  var lastW = 0, lastH = 0;

  function resize() {
    var w = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var h = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    lastW = w; lastH = h;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setSize(w, h, true);
  }
  function syncSize() {
    var w = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var h = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    if (w !== lastW || h !== lastH) resize();
  }
  window.addEventListener('resize', resize);
  window.addEventListener('orientationchange', resize);

  function render() { renderer.render(scene, camera); }

  function start() { if (rafId) return; lastT = 0; rafId = requestAnimationFrame(loop); }
  function stopLoop() { if (rafId) { cancelAnimationFrame(rafId); rafId = 0; } }

  function frameContent(snap, dt, t) {
    var a = snap.fighters[0], b = snap.fighters[1];
    // motor garantisi: fighters[0] solda (side +1, rigA), fighters[1] sağda (side -1, rigB)
    if (a) poseFighter(a, rigA, snap.t);
    if (b) poseFighter(b, rigB, snap.t);
    updateSparks(dt);
    updateConfetti(dt, snap.t);
    updateCrowd(snap.t, snap.crowdPulse);
    updateCamera(snap, dt);
    updateHud(snap);
    // vuruş anında kamera sarsıntısı (son event'ten)
    render();
  }

  function loop(ts) {
    if (!active) { rafId = 0; return; }
    rafId = requestAnimationFrame(loop);
    var t = ts / 1000;
    var dt = lastT ? Math.min(0.05, t - lastT) : 0.016;
    lastT = t;
    fpsAcc += dt; fpsN++;
    if (fpsAcc > 0.5) { fpsVal = fpsN / fpsAcc; fpsAcc = 0; fpsN = 0; }

    syncSize();
    engine.tick(dt);
    demoStep(dt);
    var snap = engine.snapshot();
    var before = frames;
    var prevHp = lastHpTotal;
    pumpEvents(snap);
    var hpNow = snap.fighters.reduce(function (s, f) { return s + f.hp; }, 0);
    if (hpNow < lastHpTotal) camShake = Math.max(camShake, 0.5);
    lastHpTotal = hpNow;
    lastSnap = snap;
    frameContent(snap, dt, t);
    frames++;

    if (frames % 600 === 0) {
      console.log('cage calisiyor n=' + frames + ' tri=' + renderer.info.render.triangles +
        ' cizim=' + renderer.info.render.calls + ' aktif=' + active);
    }
    if (renderOnce) {
      renderOnce = false;
      if (!announced) { announced = true; emit('ready', 'cage-1 yazilimGL=' + (SOFT_GL ? 1 : 0)); }
    }
  }
  var lastHpTotal = 200, lastSnap = null;

  document.addEventListener('visibilitychange', function () {
    window.cage.setActive(!document.hidden);
  });

  resize();

  /* advance kanıtı: sabit 1/60 adımlarla ileri sar */
  if (Q.advance > 0) {
    window.cage._advance(Q.advance);
    lastSnap = engine.snapshot();
    frameContent(lastSnap, 1 / 60, lastSnap.t);
    frames++;
  } else {
    var s0 = engine.snapshot();
    frameContent(s0, 1 / 60, 0);
    frames++;
  }

  if (Q.freeze) {
    active = false;
    emit('ready', 'freeze:cage-1:t=' + engine.snapshot().t.toFixed(2));
  } else {
    start();
    emit('ready', 'cage-1 yazilimGL=' + (SOFT_GL ? 1 : 0));
  }
  emit('boot', 'cage-1');
})();
