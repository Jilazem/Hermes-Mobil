/*
 * ARENA · OUTRUN — pseudo-3D synthwave yol yarışı (three.js r147, yerel asset, MIT)
 *
 * Tasarım: yol bir "ribbon" mesh; viraj/tepe ofseti vertex shader'da yol boyu
 * mesafeye göre hesaplanır (uTravel uniform'u). Böylece tüm gidişat 0 CPU maliyeti
 * ile GPU'da şekillenir; CPU yalnız oyuncu, trafik, süs ve parçacıkları günceller.
 *
 * Gömme (Android WebView):
 *   window.outrun.start() / pause() / resume() / reset() / setActive(false) / getState()
 *   Olaylar: window.__ArenaBridge.onSceneEvent(type, detail)
 *     ready | boot | start | score | crash | gameover | pause | resume | error | nowebgl
 *
 * URL test parametreleri (yalnız hata ayıklama/kanıt için):
 *   ?auto=1        menüyü atla, doğrudan yarış
 *   ?advance=6     yüklemede 6 sn simüle et (deterministik 1/60 dt) → sabit kare kanıtı
 *   ?freeze=1      advance sonrası döngüyü durdur (tek kare)
 *   ?steer=1|-1    direksiyon girdisini sabitle (kanıt)
 *   ?fps=1         FPS sayacını göster
 *   ?hud=0         DOM HUD'u gizle
 *   ?selftest=1    ~3 sn sonra ölçüm raporu yaz (#report + document.title)
 */
(function () {
  'use strict';

  /* ── 0. köprü + yardımcılar ────────────────────────────────────────────── */
  var bridge = window.__ArenaBridge || null;
  var errors = [];

  window.addEventListener('error', function (e) {
    errors.push(String((e && e.message) || e));
  });

  function emit(type, detail) {
    var d = (detail == null) ? '' : (typeof detail === 'string' ? detail : json_(detail));
    try { if (bridge && bridge.onSceneEvent) bridge.onSceneEvent(type, d); } catch (e) { /* köprü hatası sahneyi düşürmez */ }
    try { console.log('outrun ' + type + ' ' + d); } catch (e2) {}
  }
  function json_(o) { try { return JSON.stringify(o); } catch (e) { return String(o); } }
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
  // Yazılım GL (SwiftShader/llvmpipe) tespiti → AA kapatılır, preserveDrawingBuffer
  // açılır; arena3d tur-9 ölçümünde emülatörde kare aksi hâlde sunulmuyordu.
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
  function lerp(a, b, t) { return a + (b - a) * t; }
  function sign(v) { return v < 0 ? -1 : 1; }
  function rnd(a, b) { return a + Math.random() * (b - a); }
  // Deterministik PRNG — doku üretimi her koşuda aynı olsun (kanıt karşılaştırması).
  function lcg(seed) { var s = seed >>> 0; return function () { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }

  // localStorage file:// altında SecurityError atabiliyor → güvenli sarmalayıcı.
  var store = (function () {
    var mem = {};
    return {
      get: function (k) { try { return window.localStorage.getItem(k); } catch (e) { return mem[k] == null ? null : mem[k]; } },
      set: function (k, v) { try { window.localStorage.setItem(k, String(v)); } catch (e) { mem[k] = String(v); } }
    };
  })();

  function qnum(name, d) {
    var m = new RegExp('[?&]' + name + '=([^&]*)').exec(location.search);
    var v = parseFloat(m ? decodeURIComponent(m[1]) : NaN);
    return isNaN(v) ? d : v;
  }
  function qflag(name) {
    return new RegExp('[?&]' + name + '(=|&|$)').test(location.search);
  }
  function qstr(name, d) {
    var m = new RegExp('[?&]' + name + '=([^&]*)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : d;
  }

  var Q = {
    auto: qflag('auto'),
    advance: qnum('advance', 0),
    freeze: qflag('freeze'),
    steer: qflag('steer') ? qnum('steer', 0) : null,
    fps: qflag('fps') || qflag('selftest'),
    hud: qstr('hud', '1') !== '0',
    selftest: qflag('selftest')
  };

  if (typeof THREE === 'undefined') {
    showFallback('3D motoru yuklenemedi');
    emit('error', 'three-yok');
    return;
  }
  if (!hasWebGL()) {
    showFallback('3D sahne bu cihazda calismiyor');
    emit('nowebgl', 'webgl-yok');
    return;
  }

  var SOFT_GL = detectSoftGL();
  var WANT_BUFFER = SOFT_GL || Q.advance > 0 || Q.freeze || Q.selftest;

  /* ── 1. palet (synthwave) ──────────────────────────────────────────────── */
  var P = {
    bg: '#0b0416',
    fog: '#2b0749',
    skyTop: '#12002c',
    skyMid: '#3a0a5e',
    skyGlow: '#ff2d95',
    skyBelow: '#1c0433',
    mountBack: '#3a0d5e',
    mountFront: '#1d0536',
    sunCore: '#fff1b8',
    sunMid: '#ffb03a',
    sunEdge: '#ff2d95',
    road: '#171122',
    roadEdge: '#ff2d95',
    roadDash: '#eaf6ff',
    curbA: '#ff2d95',
    curbB: '#f2f6ff',
    ground: '#150936',
    gridA: '#ff2d95',
    gridB: '#29f0ff',
    neonA: '#ff2d95',
    neonB: '#29f0ff',
    neonC: '#ffd76f',
    trunk: '#3a2050',
    frond: '#0f8f86',
    player: '#29f0ff',
    playerGlow: '#29f0ff',
    carA: '#ff8a3d',
    carB: '#a45cff',
    tail: '#ff3355'
  };
  // Built-in malzemeler sRGBEncoding çıkışında parlaklığı kaydırır: hex'i lineer'e
  // çevirip veriyoruz ki ekranda TAM hex görünsün. Custom shader'lar ham hex kullanır.
  function srgbLin(hex) { return new THREE.Color(hex).convertSRGBToLinear(); }
  function col(hex) { return new THREE.Color(hex); }

  /* ── 2. gidişat (curve) matematiği — JS + GLSL aynı katsayılar ─────────── */
  // [genlik(m), frekans(1/m), faz]  — 1 birim = 1 metre
  var CX = [[34, 0.0075, 0.0], [11, 0.0170, 2.1], [3.2, 0.0380, 4.3]];
  var CY = [[7.0, 0.0070, 1.1], [2.6, 0.0150, 3.6]];

  function mkSum(terms) {
    var p = [];
    for (var i = 0; i < terms.length; i++) {
      p.push(terms[i][0].toFixed(4) + '*sin(d*' + terms[i][1].toFixed(6) + ' + ' + terms[i][2].toFixed(4) + ')');
    }
    return p.join(' + ');
  }
  function mkSlope(terms) { // türev: A*f*cos(d*f + p)
    var p = [];
    for (var i = 0; i < terms.length; i++) {
      p.push((terms[i][0] * terms[i][1]).toFixed(6) + '*cos(d*' + terms[i][1].toFixed(6) + ' + ' + terms[i][2].toFixed(4) + ')');
    }
    return p.join(' + ');
  }
  function curveX(d) { var s = 0; for (var i = 0; i < CX.length; i++) s += CX[i][0] * Math.sin(d * CX[i][1] + CX[i][2]); return s; }
  function curveY(d) { var s = 0; for (var i = 0; i < CY.length; i++) s += CY[i][0] * Math.sin(d * CY[i][1] + CY[i][2]); return s; }
  function slopeX(d) { var s = 0; for (var i = 0; i < CX.length; i++) s += CX[i][0] * CX[i][1] * Math.cos(d * CX[i][1] + CX[i][2]); return s; }

  var GLSL_CURVE =
    'float curveX(float d){ return ' + mkSum(CX) + '; }\n' +
    'float curveY(float d){ return ' + mkSum(CY) + '; }\n';

  /* ── 3. renderer / sahne / kamera ──────────────────────────────────────── */
  var STAGE = el_('stage');
  var renderer = new THREE.WebGLRenderer({
    antialias: !SOFT_GL,
    preserveDrawingBuffer: WANT_BUFFER,
    powerPreference: 'high-performance'
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(window.innerWidth, Math.max(1, window.innerHeight), true);
  renderer.outputEncoding = THREE.sRGBEncoding;
  renderer.setClearColor(col(P.bg), 1);
  STAGE.appendChild(renderer.domElement);

  var scene = new THREE.Scene();
  scene.background = col(P.bg);
  scene.fog = new THREE.Fog(srgbLin(P.fog), 70, 720);

  var FOV_BASE = 46;
  var CAM_PITCH = 3.6 * Math.PI / 180;   // sabit aşağı bakış (ufuk ~%42)
  var camera = new THREE.PerspectiveCamera(FOV_BASE, window.innerWidth / Math.max(1, window.innerHeight), 0.1, 2600);
  camera.position.set(0, 4.2, 10.5);
  camera.lookAt(0, 1.6, -70);
  scene.add(camera);

  var hemi = new THREE.HemisphereLight(srgbLin('#a06bff'), srgbLin('#12002c'), 0.75);
  scene.add(hemi);
  var dir = new THREE.DirectionalLight(srgbLin('#ffd0f0'), 0.8);
  dir.position.set(4, 12, 6);
  scene.add(dir);
  var rimA = new THREE.PointLight(srgbLin(P.neonB), 1.5, 60, 2);
  rimA.position.set(-6, 4, 2);
  scene.add(rimA);
  var rimB = new THREE.PointLight(srgbLin(P.neonA), 1.2, 60, 2);
  rimB.position.set(6, 3, -4);
  scene.add(rimB);

  /* ── 4. dokular (CanvasTexture — harici dosya YOK) ─────────────────────── */
  var TEX_REPEAT_LEN = 60; // yol dokusunun bir tekrarı kaç metre

  function canvasTex(w, h, draw, repX, repY) {
    var c = document.createElement('canvas');
    c.width = w; c.height = h;
    var ctx = c.getContext('2d');
    draw(ctx, w, h);
    var t = new THREE.CanvasTexture(c);
    t.encoding = THREE.sRGBEncoding;
    t.wrapS = repX ? THREE.RepeatWrapping : THREE.ClampToEdgeWrapping;
    t.wrapT = repY ? THREE.RepeatWrapping : THREE.ClampToEdgeWrapping;
    t.anisotropy = 4;
    return t;
  }

  // Yol yüzeyi: asfalt + orta şerit + kenar çizgisi + bordür (rumble) şeridi.
  var roadTex = canvasTex(256, 1024, function (ctx, W, H) {
    var r = lcg(1337);
    ctx.fillStyle = P.road; ctx.fillRect(0, 0, W, H);
    // asfalt greni
    for (var i = 0; i < 2600; i++) {
      var x = Math.floor(r() * W), y = Math.floor(r() * H);
      var v = 12 + Math.floor(r() * 26);
      ctx.fillStyle = 'rgba(' + v + ',' + v + ',' + (v + 8) + ',0.55)';
      ctx.fillRect(x, y, 2, 2);
    }
    // hafif dikey parlaklık bantları (ıslak asfalt hissi)
    for (var s = 0; s < 5; s++) {
      var sx = Math.floor(r() * W);
      var g = ctx.createLinearGradient(sx, 0, sx + 20, 0);
      g.addColorStop(0, 'rgba(120,90,200,0)');
      g.addColorStop(0.5, 'rgba(120,90,200,0.05)');
      g.addColorStop(1, 'rgba(120,90,200,0)');
      ctx.fillStyle = g; ctx.fillRect(sx, 0, 20, H);
    }
    // bordür: u 0..0.0755 ve 0.9245..1 → 60m'de 20 blok (3 m blok)
    var curbW = Math.round(W * 0.0755);
    for (var b = 0; b < 20; b++) {
      var y0 = b * (H / 20), y1 = H / 20;
      ctx.fillStyle = (b % 2 === 0) ? P.curbA : P.curbB;
      ctx.fillRect(0, y0, curbW, y1);
      ctx.fillRect(W - curbW, y0, curbW, y1);
    }
    // kenar neon çizgisi (bordürün hemen içi)
    var edgeW = Math.max(2, Math.round(W * 0.013));
    ctx.fillStyle = P.roadEdge;
    ctx.fillRect(curbW, 0, edgeW, H);
    ctx.fillRect(W - curbW - edgeW, 0, edgeW, H);
    // orta kesikli şerit: 60 m'de 5 kesik (6 m boya / 6 m boşluk)
    var dashW = Math.max(3, Math.round(W * 0.012));
    var dashH = H / 10;
    for (var d = 0; d < 5; d++) {
      ctx.fillStyle = P.roadDash;
      ctx.fillRect(Math.round(W / 2 - dashW / 2), d * 2 * dashH, dashW, dashH);
    }
  }, false, true);

  // Zemin grid dokusu: 20 m karo, kenarlarda neon çizgi (şeffaf zemin → mix).
  var gridTex = canvasTex(128, 128, function (ctx, W, H) {
    ctx.clearRect(0, 0, W, H);
    ctx.strokeStyle = P.gridA; ctx.lineWidth = 7; ctx.globalAlpha = 1.0;
    ctx.beginPath(); ctx.moveTo(3, 0); ctx.lineTo(3, H); ctx.moveTo(0, 3); ctx.lineTo(W, 3); ctx.stroke();
    ctx.strokeStyle = P.gridB; ctx.lineWidth = 3; ctx.globalAlpha = 0.8;
    ctx.beginPath(); ctx.moveTo(13, 0); ctx.lineTo(13, H); ctx.moveTo(0, 13); ctx.lineTo(W, 13); ctx.stroke();
    ctx.globalAlpha = 1;
  }, true, true);

  // ÖLÇÜM: custom shader'da kullanılan dokular ham (display) değer okumalı.
  // sRGBEncoding bırakılırsa three'nin doku örnekleyicisi değeri LİNEERLEŞTİRİR;
  // custom shader ise built-in malzemeler gibi yeniden kodlama yapmadığı için kare
  // karanlık çıkıyordu (ölçüm: asfalt #020104, hedef #171122). Built-in malzemeli
  // dokular (gökyüzü/dağ/güneş/tabela/parlama) sRGBEncoding kalır.
  roadTex.encoding = THREE.LinearEncoding;
  gridTex.encoding = THREE.LinearEncoding;

  // Gökyüzü: dikey gradyan + ufuk parlaması. Düzlem ekranı bire bir kapladığı için
  // (her karede ölçeklenir) ufuk çizgisi dokunun tam ortası = v 0.5.
  var skyTex = canvasTex(256, 512, function (ctx, W, H) {
    var g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0.00, '#0a0121');
    g.addColorStop(0.18, '#1a0438');
    g.addColorStop(0.34, '#33095c');
    g.addColorStop(0.44, '#6b0f74');
    g.addColorStop(0.485, '#c02a86');
    g.addColorStop(0.500, '#ff4fa3');   // ufuk çizgisi (parlak)
    g.addColorStop(0.520, '#7a1268');
    g.addColorStop(0.580, P.fog);       // sis rengiyle birebir: zemin sise karışırken dikiş olmaz
    g.addColorStop(1.00, '#0b0320');
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
    // ufukta yatay ışık havuzu (güneş çevresi yumuşak hale)
    var hg = ctx.createRadialGradient(W * 0.5, H * 0.5, 4, W * 0.5, H * 0.5, W * 0.55);
    hg.addColorStop(0.00, 'rgba(255,140,200,0.55)');
    hg.addColorStop(0.30, 'rgba(255,60,160,0.22)');
    hg.addColorStop(1.00, 'rgba(120,10,90,0)');
    ctx.fillStyle = hg; ctx.fillRect(0, 0, W, H);
  }, false, false);

  // Dağlar: iki katman silüet. Tabanı ufka oturur (doku alt kenarı = ufuk çizgisi),
  // 32:1 oran → 5200 m genişlik / 162 m yükseklik; tepeler ufkun ~6° üstünde kalır.
  var mountTex = canvasTex(4096, 128, function (ctx, W, H) {
    ctx.clearRect(0, 0, W, H);
    function range(amp, step, seed, color, glow) {
      var r = lcg(seed);
      ctx.beginPath();
      ctx.moveTo(0, H);
      for (var x = 0; x <= W; x += step) {
        var y = H - Math.abs(Math.sin(x * 0.0009 + seed) * amp) - r() * amp * 0.5;
        ctx.lineTo(x, y);
      }
      ctx.lineTo(W, H); ctx.closePath();
      ctx.fillStyle = color; ctx.fill();
      if (glow) { ctx.strokeStyle = glow; ctx.lineWidth = 1.5; ctx.globalAlpha = 0.5; ctx.stroke(); ctx.globalAlpha = 1; }
    }
    range(H * 0.62, 12, 7, P.mountBack, '#ff2d95');  // arka sıra (uzak, yüksek)
    range(H * 0.46, 8, 23, P.mountFront, '#29f0ff'); // ön sıra (yakın, alçak)
  }, false, false);

  // Güneş: şerit kesikli retro disk (additive).
  var sunTex = canvasTex(512, 512, function (ctx, W, H) {
    ctx.clearRect(0, 0, W, H);
    var cxp = W / 2, cyp = H / 2, R = W * 0.47;
    var g = ctx.createRadialGradient(cxp, cyp - R * 0.15, R * 0.05, cxp, cyp, R);
    g.addColorStop(0.00, P.sunCore);
    g.addColorStop(0.45, P.sunMid);
    g.addColorStop(0.85, P.sunEdge);
    g.addColorStop(1.00, 'rgba(255,45,149,0)');
    ctx.fillStyle = g;
    ctx.beginPath(); ctx.arc(cxp, cyp, R, 0, Math.PI * 2); ctx.fill();
    // şeritler: aşağı indikçe kalınlaşan saydam boşluklar
    ctx.globalCompositeOperation = 'destination-out';
    var y = cyp + R * 0.02, hgt = 3;
    while (y < cyp + R) {
      ctx.fillRect(0, y, W, hgt);
      y += hgt * 2.05;
      hgt += 1.35;
    }
    ctx.globalCompositeOperation = 'source-over';
    // dış halo
    var hg = ctx.createRadialGradient(cxp, cyp, R * 0.9, cxp, cyp, W * 0.5);
    hg.addColorStop(0, 'rgba(255,45,149,0.35)');
    hg.addColorStop(1, 'rgba(255,45,149,0)');
    ctx.fillStyle = hg; ctx.beginPath(); ctx.arc(cxp, cyp, W * 0.5, 0, Math.PI * 2); ctx.fill();
  }, false, false);

  // Neon tabela: "OUTRUN" + chevron.
  var signTex = canvasTex(512, 256, function (ctx, W, H) {
    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = 'rgba(10,3,22,0.86)';
    ctx.fillRect(0, 0, W, H);
    ctx.strokeStyle = P.neonB; ctx.lineWidth = 6; ctx.strokeRect(8, 8, W - 16, H - 16);
    ctx.strokeStyle = P.neonA; ctx.lineWidth = 2; ctx.strokeRect(20, 20, W - 40, H - 40);
    ctx.font = 'bold 96px system-ui, -apple-system, "Roboto", sans-serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.shadowColor = P.neonA; ctx.shadowBlur = 26;
    ctx.fillStyle = '#fff2fb';
    ctx.fillText('OUTRUN', W / 2, H / 2 - 6);
    ctx.shadowBlur = 0;
    ctx.fillStyle = P.neonC;
    for (var i = 0; i < 3; i++) {
      ctx.beginPath();
      var y = H - 44 + i * 7;
      ctx.moveTo(W / 2 - 60, y); ctx.lineTo(W / 2, y + 10); ctx.lineTo(W / 2 + 60, y);
      ctx.lineWidth = 3; ctx.strokeStyle = P.neonC; ctx.stroke();
    }
  }, false, false);

  // Araç altı parlaması (additive radial).
  var glowTex = canvasTex(256, 256, function (ctx, W, H) {
    var g = ctx.createRadialGradient(W / 2, H / 2, 4, W / 2, H / 2, W / 2);
    g.addColorStop(0, 'rgba(41,240,255,0.85)');
    g.addColorStop(0.45, 'rgba(41,240,255,0.28)');
    g.addColorStop(1, 'rgba(41,240,255,0)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
  }, false, false);

  /* ── 5. yol + zemin mesh'leri (custom shader, mesafeye göre viraj/tepe) ── */
  var RH = 4.8;          // yol yarım genişliği (m) — 9.6 m asfalt
  var ROWS = 180, SEG = 5;          // yol satırı: 180 × 5 m = 900 m
  var COLS = [-1.18, -1.00, -0.60, -0.10, 0.10, 0.60, 1.00, 1.18];

  var FOG_NEAR = 70, FOG_FAR = 720;

  function buildRibbon(rows, step, cols, texScale, baseY, z0) {
    var i, j, n = cols.length;
    var pos = [], uv = [], idx = [];
    var start = (z0 == null) ? 0 : z0; // z0 > 0: kamera arkasına da şerit uzat (ekran dibi boş kalmasın)
    for (i = 0; i <= rows; i++) {
      var z = start - i * step;
      for (j = 0; j < n; j++) {
        pos.push(cols[j], baseY, z);
        uv.push((cols[j] / RH + 1.18) / 2.36, (-z) * texScale);
      }
    }
    for (i = 0; i < rows; i++) {
      for (j = 0; j < n - 1; j++) {
        var a = i * n + j, b = a + 1, c = a + n, d = c + 1;
        idx.push(a, b, c, b, d, c);
      }
    }
    var g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    g.setIndex(idx);
    return g;
  }

  var roadUni = {
    uMap: { value: roadTex },
    uTravel: { value: 0 },
    uFog: { value: col(P.fog) },
    uFogNear: { value: FOG_NEAR },
    uFogFar: { value: FOG_FAR },
    uGain: { value: 1.0 }
  };
  var ribbonVS = [
    'precision highp float;',
    // DİKKAT: three, ShaderMaterial'a position/normal/uv attribute'larını ve
    // modelViewMatrix/projectionMatrix uniform'larını KENDİSİ ekliyor —
    // yeniden tanımlamak 'redefinition' derleme hatası verir (ölçüldü).
    'uniform float uTravel;',
    'varying vec2 vUv; varying float vDepth;',
    GLSL_CURVE,
    'void main(){',
    '  float d = uTravel - position.z;',
    '  vec3 p = position;',
    '  p.x += curveX(d) - curveX(uTravel);',
    '  p.y += curveY(d) - curveY(uTravel);',
    '  vec4 mv = modelViewMatrix * vec4(p, 1.0);',
    '  vDepth = -mv.z;',
    '  vUv = uv;',
    '  gl_Position = projectionMatrix * mv;',
    '}'
  ].join('\n');
  var ribbonFS = [
    'precision highp float;',
    'uniform sampler2D uMap;',
    'uniform vec3 uFog; uniform float uFogNear; uniform float uFogFar; uniform float uGain;',
    'varying vec2 vUv; varying float vDepth;',
    'void main(){',
    '  vec3 c = texture2D(uMap, vUv).rgb * uGain;',
    '  float f = clamp((vDepth - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);',
    '  gl_FragColor = vec4(mix(c, uFog, f), 1.0);',
    '}'
  ].join('\n');

  var road = new THREE.Mesh(buildRibbon(ROWS, SEG, COLS.map(function (c) { return c * RH; }), 1 / TEX_REPEAT_LEN, 0, 42), new THREE.ShaderMaterial({
    uniforms: roadUni, vertexShader: ribbonVS, fragmentShader: ribbonFS
  }));
  road.frustumCulled = false;
  scene.add(road);

  // Zemin: geniş ızgara, aynı gidişatla bükülür.
  var G_COLS = [-330, -26, -10, 10, 26, 330];
  var groundUni = {
    uMap: { value: gridTex },
    uTravel: { value: 0 },
    uFog: { value: col(P.fog) },
    uFogNear: { value: FOG_NEAR },
    uFogFar: { value: FOG_FAR },
    uBase: { value: col(P.ground) },
    uGrid: { value: 20.0 }
  };
  var groundVS = [
    'precision highp float;',
    'uniform float uTravel; uniform float uGrid;',
    'varying vec2 vUv; varying float vDepth; varying float vD;',
    GLSL_CURVE,
    'void main(){',
    '  float d = uTravel - position.z;',
    '  vec3 p = position;',
    '  p.x += curveX(d) - curveX(uTravel);',
    '  p.y += curveY(d) - curveY(uTravel);',
    '  vec4 mv = modelViewMatrix * vec4(p, 1.0);',
    '  vDepth = -mv.z;',
    '  vD = d;',
    '  vUv = vec2(position.x / uGrid, d / uGrid);',
    '  gl_Position = projectionMatrix * mv;',
    '}'
  ].join('\n');
  var groundFS = [
    'precision highp float;',
    'uniform sampler2D uMap; uniform vec3 uBase; uniform vec3 uFog;',
    'uniform float uFogNear; uniform float uFogFar;',
    'varying vec2 vUv; varying float vDepth; varying float vD;',
    'void main(){',
    '  vec4 g = texture2D(uMap, vUv);',
    '  vec3 c = mix(uBase, g.rgb, g.a);',
    // ÖLÇÜM: renkler ham (display) float olduğu için "hafif" katkı bile baskın
    // çıkıyor — ilk sürümde +0.05/+0.10 eklenince zemin komple sis rengine
    // dönüyordu (#1f0749). Katsayılar ~4 kat kısıldı.
    '  float yakin = 1.0 - clamp((vDepth - 30.0) / 340.0, 0.0, 1.0);',
    '  c += vec3(0.014, 0.003, 0.030) * yakin;',
    '  float f = clamp((vDepth - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);',
    '  gl_FragColor = vec4(mix(c, uFog, f), 1.0);',
    '}'
  ].join('\n');

  var ground = new THREE.Mesh(buildRibbon(112, 9, G_COLS, 0, -0.02, 54), new THREE.ShaderMaterial({
    uniforms: groundUni, vertexShader: groundVS, fragmentShader: groundFS
  }));
  ground.frustumCulled = false;
  scene.add(ground);

  /* ── 6. arka plan: gökyüzü, dağlar, güneş ──────────────────────────────── */
  // KATMAN SIRASI ÖLÇÜMÜ: bu katmanlar saydam (transparent) ve three onları opak
  // nesnelerden SONRA çizer. depthTest:false bırakılırsa yol/zemin/araçların
  // ÜZERİNE boyanıyordu (ilk kare kanıtı: ekranın tamamı gökyüzü+dağ). Bu yüzden
  // depthTest AÇIK; katmanlar 880-950 m'de, sisin (720 m) ötesinde durur.
  var SKY_D = 950;
  var MOUNT_D = 880;
  var MOUNT_H = 162;   // dünya yüksekliği: tepeler ufkun ~6° üstünde kalsın

  function backdropMesh(tex, w, h, blend, order) {
    var m = new THREE.Mesh(
      new THREE.PlaneGeometry(w == null ? 1 : w, h == null ? 1 : h),
      new THREE.MeshBasicMaterial({
        map: tex, transparent: true, depthWrite: false, depthTest: true, fog: false,
        blending: blend || THREE.NormalBlending
      })
    );
    m.renderOrder = order;
    m.frustumCulled = false;
    scene.add(m);
    return m;
  }
  var skyMesh = backdropMesh(skyTex, 1, 1, THREE.NormalBlending, -30);   // ekranı tam kaplar (ölçek her karede)
  var mountMesh = backdropMesh(mountTex, 5200, MOUNT_H, THREE.NormalBlending, -20);
  var sunMesh = backdropMesh(sunTex, 280, 280, THREE.AdditiveBlending, -10);

  /* ── 7. yol kenarı süsleri (instanced → 1 draw call / grup) ────────────── */
  var _m4 = new THREE.Matrix4(), _q = new THREE.Quaternion(), _e = new THREE.Euler(), _v3 = new THREE.Vector3();
  function makeMatrix(x, y, z, ry, s) {
    var k = (s == null ? 1 : s);
    _e.set(0, ry || 0, 0);
    _q.setFromEuler(_e);
    _v3.set(k, k, k);
    _m4.compose(new THREE.Vector3(x, y, z), _q, _v3);
    return _m4;
  }

  var POST_N = 30, POST_STEP = 44;
  var postGeo = new THREE.BoxGeometry(0.18, 6.4, 0.18);
  postGeo.translate(0, 3.2, 0);
  var postTopGeo = new THREE.BoxGeometry(0.62, 0.62, 0.62);
  postTopGeo.translate(0, 6.7, 0);
  var ok = false;
  var posts = new THREE.InstancedMesh(postGeo, new THREE.MeshBasicMaterial({ color: srgbLin(P.neonB), fog: true }), POST_N);
  var postTops = new THREE.InstancedMesh(postTopGeo, new THREE.MeshBasicMaterial({ color: srgbLin(P.neonA), fog: true }), POST_N);
  [posts, postTops].forEach(function (m) { m.frustumCulled = false; scene.add(m); });

  // Palmiye: gövde + 6 yaprak (iki instanced mesh aynı transform'u paylaşır).
  var PALM_N = 14, PALM_STEP = 82;
  var trunkGeo = new THREE.CylinderGeometry(0.26, 0.46, 7.0, 6, 1, true);
  trunkGeo.translate(0, 3.5, 0);
  var frondParts = [];
  for (var fi = 0; fi < 6; fi++) {
    var pg = new THREE.PlaneGeometry(4.6, 1.15, 1, 1);
    var m4 = new THREE.Matrix4();
    m4.makeRotationZ(-0.34);
    var e2 = new THREE.Euler(0, (fi / 6) * Math.PI * 2, 0);
    m4.premultiply(new THREE.Matrix4().makeRotationFromEuler(e2));
    m4.setPosition(0, 7.0, 0);
    pg.applyMatrix4(m4);
    frondParts.push(pg);
  }
  var frondGeo = mergeGeos(frondParts);
  var palms = new THREE.InstancedMesh(trunkGeo, new THREE.MeshLambertMaterial({ color: srgbLin(P.trunk), fog: true }), PALM_N);
  var fronds = new THREE.InstancedMesh(frondGeo, new THREE.MeshLambertMaterial({ color: srgbLin(P.frond), side: THREE.DoubleSide, fog: true }), PALM_N);
  [palms, fronds].forEach(function (m) { m.frustumCulled = false; scene.add(m); });

  // Tabelalar
  var SIGN_N = 4, SIGN_STEP = 330;
  var signGeo = new THREE.PlaneGeometry(11, 5.5);
  var signs = new THREE.InstancedMesh(signGeo, new THREE.MeshBasicMaterial({ map: signTex, fog: true }), SIGN_N);
  signs.frustumCulled = false;
  scene.add(signs);

  function mergeGeos(list) {
    var pos = [], nor = [], uv = [], k, i;
    for (k = 0; k < list.length; k++) {
      var g = list[k].index ? list[k].toNonIndexed() : list[k];
      var p = g.getAttribute('position'), n = g.getAttribute('normal'), u = g.getAttribute('uv');
      for (i = 0; i < p.count; i++) {
        pos.push(p.getX(i), p.getY(i), p.getZ(i));
        if (n) nor.push(n.getX(i), n.getY(i), n.getZ(i));
        if (u) uv.push(u.getX(i), u.getY(i));
      }
    }
    var out = new THREE.BufferGeometry();
    out.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    if (nor.length) out.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
    if (uv.length) out.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    return out;
  }

  /* ── 8. araçlar (low-poly, birleştirilmiş geometri) ────────────────────── */
  function part(geo, x, y, z, rx, ry, rz, sx, sy, sz) {
    var m = new THREE.Matrix4();
    m.makeRotationFromEuler(new THREE.Euler(rx || 0, ry || 0, rz || 0));
    m.scale(new THREE.Vector3(sx == null ? 1 : sx, sy == null ? 1 : sy, sz == null ? 1 : sz));
    m.setPosition(x || 0, y || 0, z || 0);
    geo.applyMatrix4(m);
    return geo;
  }
  var B = THREE.BoxGeometry, C = THREE.CylinderGeometry;

  // Gövde: burun -z yönünde (ileri).
  function carBody() {
    return mergeGeos([
      part(new B(1.94, 0.44, 4.30), 0, 0.46, 0),                    // ana gövde
      part(new B(1.78, 0.30, 1.20), 0, 0.36, -2.25),                // burun
      part(new B(1.50, 0.46, 1.70), 0, 0.86, 0.18),                 // kabin
      part(new B(1.36, 0.40, 1.42), 0, 0.92, 0.10),                 // cam (koyu)
      part(new B(2.00, 0.10, 0.46), 0, 1.06, 2.00),                 // spoiler
      part(new B(0.16, 0.26, 0.16), -0.72, 0.92, 1.98),             // spoiler ayak
      part(new B(0.16, 0.26, 0.16), 0.72, 0.92, 1.98),
      part(new C(0.44, 0.44, 0.32, 10), -0.99, 0.44, -1.42, 0, 0, Math.PI / 2),
      part(new C(0.44, 0.44, 0.32, 10), 0.99, 0.44, -1.42, 0, 0, Math.PI / 2),
      part(new C(0.46, 0.46, 0.34, 10), -0.99, 0.46, 1.44, 0, 0, Math.PI / 2),
      part(new C(0.46, 0.46, 0.34, 10), 0.99, 0.46, 1.44, 0, 0, Math.PI / 2)
    ]);
  }
  var carGeo = carBody();
  // Neon parçalar: farlar (ön) + stop (arka) + difüzör çizgisi.
  var carNeonGeo = mergeGeos([
    part(new B(0.46, 0.13, 0.10), -0.62, 0.52, -2.80),
    part(new B(0.46, 0.13, 0.10), 0.62, 0.52, -2.80),
    part(new B(0.66, 0.15, 0.10), -0.56, 0.60, 2.18),
    part(new B(0.66, 0.15, 0.10), 0.56, 0.60, 2.18),
    part(new B(1.70, 0.06, 0.10), 0, 0.24, 2.14)
  ]);

  var playerMat = new THREE.MeshPhongMaterial({ color: srgbLin(P.player), emissive: srgbLin('#08323c'), shininess: 90, specular: srgbLin('#ffffff') });
  var player = new THREE.Group();
  var playerBody = new THREE.Mesh(carGeo, playerMat);
  var playerNeon = new THREE.Mesh(carNeonGeo, new THREE.MeshBasicMaterial({ color: srgbLin('#ffe9ff') }));
  var playerGlow = new THREE.Mesh(new THREE.PlaneGeometry(9.5, 15), new THREE.MeshBasicMaterial({ map: glowTex, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false }));
  playerGlow.rotation.x = -Math.PI / 2;
  playerGlow.position.y = 0.03;
  player.add(playerBody); player.add(playerNeon); player.add(playerGlow);
  scene.add(player);

  // Trafik: iki renk grubu, her grup instanced (2 gövde + 2 stop = 4 draw call).
  var TRAFFIC_N = 6;
  function trafficGroup(count, hexColor) {
    var body = new THREE.InstancedMesh(carGeo, new THREE.MeshPhongMaterial({
      color: srgbLin(hexColor), emissive: srgbLin('#1a0620'), shininess: 70, specular: srgbLin('#ffffff')
    }), count);
    body.frustumCulled = false;
    scene.add(body); body.userData.ok = true;
    return body;
  }
  var trafficA = trafficGroup(3, P.carA);
  var trafficB = trafficGroup(3, P.carB);

  /* ── 9. parçacıklar: hız çizgileri + çarpışma kıvılcımları ─────────────── */
  var STREAK_N = 190;
  var streakPos = new Float32Array(STREAK_N * 6);
  var streakGeo = new THREE.BufferGeometry();
  streakGeo.setAttribute('position', new THREE.BufferAttribute(streakPos, 3));
  var streakMat = new THREE.LineBasicMaterial({ color: srgbLin('#bff6ff'), transparent: true, opacity: 0.35, blending: THREE.AdditiveBlending, depthWrite: false, fog: false });
  var streaks = new THREE.LineSegments(streakGeo, streakMat);
  streaks.frustumCulled = false;
  scene.add(streaks);
  var streakState = [];
  for (var si = 0; si < STREAK_N; si++) streakState.push({ x: rnd(-22, 22), y: rnd(0.5, 11), z: rnd(-120, 12) });

  var SPARK_N = 64;
  var sparkPos = new Float32Array(SPARK_N * 3);
  var sparkGeo = new THREE.BufferGeometry();
  sparkGeo.setAttribute('position', new THREE.BufferAttribute(sparkPos, 3));
  var sparkMat = new THREE.PointsMaterial({ color: srgbLin('#ffd76f'), size: 0.5, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true, fog: false });
  var sparks = new THREE.Points(sparkGeo, sparkMat);
  sparks.frustumCulled = false;
  scene.add(sparks);
  var sparkState = [];
  for (var pi = 0; pi < SPARK_N; pi++) sparkState.push({ x: 0, y: -99, z: 0, vx: 0, vy: 0, vz: 0, life: 0 });

  /* ── 10. HUD / DOM ────────────────────────────────────────────────────── */
  var U = {
    dist: el_('distV'), best: el_('bestV'), spd: el_('spdV'), fps: el_('fps'),
    startBest: el_('startBest'), pauseDist: el_('pauseDist'), pauseBest: el_('pauseBest'),
    overBest: el_('overBest'), overCrash: el_('overCrash'), scoreBig: el_('scoreBig'),
    scrStart: el_('screenStart'), scrPause: el_('screenPause'), scrOver: el_('screenOver'),
    hud: el_('hud'), touchHint: el_('touchHint'), boost: el_('boostBtn'), touch: el_('touch')
  };
  if (!Q.hud) U.hud.style.display = 'none';
  if (Q.fps) U.fps.style.display = 'block'; else U.fps.style.display = 'none';

  function showScreen(name) {
    U.scrStart.className = 'scrim' + (name === 'start' ? '' : ' hidden');
    U.scrPause.className = 'scrim' + (name === 'pause' ? '' : ' hidden');
    U.scrOver.className = 'scrim' + (name === 'over' ? '' : ' hidden');
  }

  /* ── 11. oyun durumu ──────────────────────────────────────────────────── */
  var MAX_SPEED = 92;      // m/s (~331 km/s)
  var BOOST_MUL = 1.26;
  var OFF_MAX = 27;        // yol dışı tavan hız
  var BEST_KEY = 'outrun.best.v1';

  var S = {
    state: 'menu', travel: 0, speed: 0, playerX: 0,
    steerIn: 0, steer: 0, boost: false, brake: false,
    offT: 0, shake: 0, crashes: 0, score: 0, time: 0,
    best: parseInt(store.get(BEST_KEY) || '0', 10) || 0,
    bestShown: -1, lastCrash: -9
  };
  var cam = { x: 0, yaw: 0 };
  var traffic = [];
  for (var ti = 0; ti < TRAFFIC_N; ti++) {
    traffic.push({ dist: 260 + ti * 230 + rnd(0, 90), lane: rnd(-3.4, 3.4), v: rnd(24, 44), grp: ti < 3 ? 0 : 1, idx: ti % 3 });
  }
  var PROP_ORIGIN = 0;
  var signState = [];

  function resetRun(keepMenu) {
    S.travel = 0; S.speed = 0; S.playerX = 0; S.offT = 0; S.crashes = 0; S.score = 0;
    S.shake = 0; S.steer = 0; S.steerIn = 0; S.time = 0;
    cam.x = 0; cam.yaw = 0;
    for (var i = 0; i < traffic.length; i++) {
      traffic[i].dist = 300 + i * 220 + rnd(0, 80);
      traffic[i].lane = rnd(-3.4, 3.4);
      traffic[i].v = rnd(24, 44);
    }
    for (var j = 0; j < sparkState.length; j++) { sparkState[j].life = 0; sparkState[j].y = -99; }
    if (!keepMenu) updateHud(true);
  }

  function updateBest() {
    if (S.score > S.best) {
      S.best = S.score;
      store.set(BEST_KEY, S.best);
      if (U.startBest) U.startBest.textContent = String(S.best);
      return true;
    }
    return false;
  }

  /* ── 12. girdi (klavye + dokunmatik + sürükleme) ──────────────────────── */
  var keys = { left: false, right: false, up: false, down: false };
  var ptr = { id: null, startX: 0, x: 0, dir: 0, t0: 0 };
  var btnBoost = false;

  window.addEventListener('keydown', function (e) {
    var k = e.key;
    if (k === 'ArrowLeft' || k === 'a' || k === 'A') { keys.left = true; e.preventDefault(); }
    else if (k === 'ArrowRight' || k === 'd' || k === 'D') { keys.right = true; e.preventDefault(); }
    else if (k === 'ArrowUp' || k === 'w' || k === 'W') { keys.up = true; e.preventDefault(); }
    else if (k === 'ArrowDown' || k === 's' || k === 'S') { keys.down = true; e.preventDefault(); }
    else if (k === 'p' || k === 'P' || k === 'Escape') { togglePause(); }
    else if (k === 'Enter' || k === ' ') {
      if (S.state === 'menu' || S.state === 'over') startRun();
      else togglePause();
      e.preventDefault();
    }
  });
  window.addEventListener('keyup', function (e) {
    var k = e.key;
    if (k === 'ArrowLeft' || k === 'a' || k === 'A') keys.left = false;
    else if (k === 'ArrowRight' || k === 'd' || k === 'D') keys.right = false;
    else if (k === 'ArrowUp' || k === 'w' || k === 'W') keys.up = false;
    else if (k === 'ArrowDown' || k === 's' || k === 'S') keys.down = false;
  });

  if (U.touch) {
    U.touch.addEventListener('pointerdown', function (e) {
      if (ptr.id !== null) return;
      ptr.id = e.pointerId;
      ptr.startX = e.clientX; ptr.x = e.clientX;
      ptr.dir = (e.clientX < (window.innerWidth / 2)) ? -1 : 1;
      ptr.t0 = (window.performance && performance.now ? performance.now() : Date.now());
      try { U.touch.setPointerCapture(e.pointerId); } catch (err) {}
      e.preventDefault();
    }, { passive: false });
    U.touch.addEventListener('pointermove', function (e) {
      if (e.pointerId === ptr.id) ptr.x = e.clientX;
    }, { passive: false });
    function release(e) {
      if (e.pointerId === ptr.id) { ptr.id = null; }
    }
    U.touch.addEventListener('pointerup', release);
    U.touch.addEventListener('pointercancel', release);
    U.touch.addEventListener('pointerleave', release);
  }

  function readSteer() {
    var s = 0;
    if (ptr.id !== null) {
      var now = (window.performance && performance.now ? performance.now() : Date.now());
      var held = (now - ptr.t0) / 1000;
      var mag = Math.min(1, 0.62 + held * 1.5);
      var dx = (ptr.x - ptr.startX) / (window.innerWidth * 0.22);
      s = ptr.dir * mag + dx;
    }
    if (keys.left) s -= 1;
    if (keys.right) s += 1;
    return clamp(s, -1, 1);
  }

  // Butonlar
  function bind(id, fn) { var b = el_(id); if (b) b.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); fn(); }); }
  bind('startBtn', function () { startRun(); });
  bind('resumeBtn', function () { setPaused(false); });
  bind('restartBtn', function () { resetRun(); startRun(); });
  bind('againBtn', function () { resetRun(); startRun(); });
  bind('pauseBtn', function () { togglePause(); });
  // Ekrana dokun-geç: başlangıç ve bitiş ekranının boş alanı da başlatır
  // (butonlar stopPropagation ettiği için çift tetiklenme olmaz).
  if (U.scrStart) U.scrStart.addEventListener('click', function () { if (S.state === 'menu') startRun(); });
  if (U.scrOver) U.scrOver.addEventListener('click', function () { if (S.state === 'over') { resetRun(); startRun(); } });
  if (U.boost) {
    var boostOn = function (e) { if (e) e.preventDefault(); btnBoost = true; U.boost.className = 'on'; };
    var boostOff = function (e) { if (e) e.preventDefault(); btnBoost = false; U.boost.className = ''; };
    U.boost.addEventListener('pointerdown', boostOn);
    U.boost.addEventListener('pointerup', boostOff);
    U.boost.addEventListener('pointercancel', boostOff);
    U.boost.addEventListener('pointerleave', boostOff);
  }

  /* ── 13. durum geçişleri (dış API'nin de kullandığı çekirdek) ─────────── */
  function startRun(silent) {
    resetRun(true);
    S.state = 'running';
    S.speed = 12;
    showScreen('none');
    if (U.touchHint) { U.touchHint.className = ''; setTimeout(function () { if (U.touchHint) U.touchHint.className = 'off'; }, 6000); }
    if (!silent) emit('start', 'beste:' + S.best);
  }
  function setPaused(on) {
    if (on && S.state === 'running') {
      S.state = 'paused';
      U.pauseDist.textContent = String(S.score);
      U.pauseBest.textContent = String(Math.max(S.best, S.score));
      showScreen('pause');
      emit('pause', String(S.score));
    } else if (!on && S.state === 'paused') {
      S.state = 'running';
      showScreen('none');
      emit('resume', String(S.score));
    }
  }
  function togglePause() {
    if (S.state === 'running') setPaused(true);
    else if (S.state === 'paused') setPaused(false);
    else if (S.state === 'menu' || S.state === 'over') startRun();
  }
  function gameOver(reason) {
    if (S.state !== 'running') return;
    S.state = 'over';
    var nb = updateBest();
    U.scoreBig.textContent = String(S.score);
    U.overBest.textContent = String(S.best);
    U.overCrash.textContent = String(S.crashes);
    showScreen('over');
    emit('gameover', { mesafe: S.score, en_iyi: S.best, carpisma: S.crashes, sebep: reason, yeni_rekor: nb });
  }

  /* ── 14. simülasyon adımı ─────────────────────────────────────────────── */
  function roadX(d, travel) { return curveX(d) - curveX(travel); }
  function roadY(d, travel) { return curveY(d) - curveY(travel); }

  function speedPercent() { return clamp(S.speed / MAX_SPEED, 0, 1.4); }

  function crash() {
    if (S.time - S.lastCrash < 0.9) return;
    S.lastCrash = S.time;
    S.crashes++;
    S.speed = Math.max(14, S.speed * 0.34);
    S.shake = 1;
    burstSparks(S.playerX, 0.9, 1.6);
    emit('crash', { n: S.crashes, kmh: Math.round(S.speed * 3.6), mesafe: S.score });
  }

  function step(dt) {
    S.time += dt;
    var spdP = speedPercent();
    var menu = (S.state === 'menu');

    if (S.state === 'running' || menu) {
      // ── hız ──
      var off = Math.abs(S.playerX) > RH + 0.4;
      if (menu) {
        S.speed = 42;
        S.steerIn = Math.sin(S.time * 0.35) * 0.55;
        S.playerX = Math.sin(S.time * 0.42) * 2.0;
      } else {
        var target = MAX_SPEED * (S.boost ? BOOST_MUL : 1);
        if (off) target = OFF_MAX * 0.6;
        if (S.brake) S.speed = Math.max(0, S.speed - 52 * dt);
        else S.speed += (target - S.speed) * Math.min(1, dt * (S.boost ? 0.9 : 0.55));
        S.speed = clamp(S.speed, 0, MAX_SPEED * BOOST_MUL);

        // ── direksiyon ──
        S.steerIn = (Q.steer != null) ? Q.steer : readSteer();
        S.steer += (S.steerIn - S.steer) * Math.min(1, dt * 9);
        var grip = 0.3 + 0.7 * clamp(spdP, 0, 1);
        // Yanal hız tavanı 8.5 m/s: tam kilitte ~1.2 sn'de şerit değişir (13 m/s
        // ile 0.7 sn'de tüm yolu geçiyordu — ölçüm sonrası daha sürülebilir ayar).
        S.playerX += S.steer * 8.5 * grip * dt * (off ? 0.55 : 1);
        // merkezkaç: virajda dışa savrulma (direksiyonla dengelenir)
        S.playerX -= slopeX(S.travel + 55) * clamp(spdP, 0, 1) * clamp(spdP, 0, 1) * 8 * dt;
        // Girdi yokken hafif şerit yardımı: araç kendiliğinden yolda kalır
        // (arena vitrini/attract mode ve otomatik test için; direksiyona karışmaz).
        if (Math.abs(S.steerIn) < 0.05 && !off) {
          S.playerX -= S.playerX * Math.min(1, dt * 0.9);
        }

        // ── yol dışı ──
        if (off) {
          S.playerX += sign(S.playerX) * 1.6 * dt;
          S.offT += dt;
          S.shake = Math.max(S.shake, 0.22);
          if (S.offT > 2.6) { gameOver('seritten-cikti'); }
        } else {
          S.offT = Math.max(0, S.offT - dt * 1.6);
        }
        S.score = Math.floor(S.travel);
      }
      S.travel += S.speed * dt;
      S.boost = !!(keys.up || btnBoost);   // klavye ↑ veya GAZ butonu
      S.brake = !!keys.down;
    }

    if (S.state === 'paused') { S.steer *= 0.9; }

    // ── trafik ──
    var i, t;
    for (i = 0; i < traffic.length; i++) {
      t = traffic[i];
      t.dist += t.v * dt;
      if (t.dist < S.travel - 40 || t.dist > S.travel + 2200) {
        t.dist = S.travel + 800 + rnd(0, 900);
        t.lane = rnd(-3.4, 3.4);
        t.v = rnd(24, 46);
      }
      if (S.state === 'running' && !menu) {
        var dz = t.dist - S.travel;
        if (dz > -2.6 && dz < 4.6 && Math.abs(t.lane - S.playerX) < 1.85) {
          crash();
          t.dist = S.travel + 26; // çarpışan aracı öne it, çift sayım olmasın
        } else if (dz > 0 && dz < 14 && Math.abs(t.lane - S.playerX) < 3.2) {
          S.shake = Math.max(S.shake, 0.12); // yakın geçiş
        }
      }
    }

    // ── sarsıntı sönümü ──
    S.shake = Math.max(0, S.shake - dt * 2.2);
    S.shake *= (1 - Math.min(0.6, dt * 1.2));

    updateCamera(dt);
    updateProps();
    updateParticles(dt);
    if (S.state === 'running') updateHud(false);
  }

  /* ── 15. görsel güncellemeler ─────────────────────────────────────────── */
  function updateCamera(dt) {
    var spdP = clamp(speedPercent(), 0, 1);
    var lx = roadX(S.travel + 55, S.travel);
    var ly = roadY(S.travel + 55, S.travel);
    var targetYaw = Math.atan2(S.playerX * 0.35 + lx * 0.75 - cam.x, 55);
    cam.yaw += (targetYaw - cam.yaw) * Math.min(1, dt * 2.8);
    cam.x += (S.playerX * 0.62 - cam.x) * Math.min(1, dt * 5);

    var sh = S.shake;
    var sx = (Math.random() - 0.5) * sh * 1.1;
    var sy = (Math.random() - 0.5) * sh * 0.7;
    var lookDist = 70;
    camera.position.set(cam.x + sx, 4.0 + spdP * 0.5 + ly * 0.26 + sy, 10.0 + spdP * 1.6);
    // Sabit aşağı bakış açısı: ufuk ekranda ~%42'de kalır (klasik outrun çerçevesi).
    var lookY = camera.position.y - Math.tan(CAM_PITCH) * lookDist + ly * 0.5 + spdP * 0.1;
    camera.lookAt(camera.position.x + Math.sin(cam.yaw) * lookDist, lookY, -lookDist);
    camera.rotateZ(-S.steer * 0.03 + (Math.random() - 0.5) * sh * 0.06);

    var fovT = FOV_BASE + spdP * 15 + ((S.boost && S.state === 'running') ? 5 : 0);
    camera.fov += (fovT - camera.fov) * Math.min(1, dt * 3.2);
    camera.updateProjectionMatrix();

    // Arka plan katmanları: gökyüzü düzlemi ekranı bire bir kaplar (ufuk dokunun
    // ortasında kalır), dağların tabanı ufka oturur, güneş kaçış noktasını izler.
    var vanish = clamp(slopeX(S.travel + 160) * SKY_D * 0.55, -1200, 1200);
    var skyH = 2 * SKY_D * Math.tan(camera.fov * Math.PI / 360) * 1.15;
    skyMesh.scale.set(skyH * camera.aspect, skyH, 1);
    skyMesh.position.set(camera.position.x, camera.position.y, camera.position.z - SKY_D);
    mountMesh.position.set(camera.position.x + vanish * 0.65, camera.position.y + MOUNT_H / 2, camera.position.z - MOUNT_D);
    sunMesh.position.set(camera.position.x + vanish, camera.position.y + 128, camera.position.z - 940);

    // yol/lazer ışıkları aracı takip eder
    rimA.position.set(camera.position.x - 7, 4.5, camera.position.z - 6);
    rimB.position.set(camera.position.x + 7, 3.5, camera.position.z + 2);
    player.position.set(S.playerX, 0, 0);
    player.rotation.z = -S.steer * 0.075;
    player.rotation.y = -S.steer * 0.10;
    player.rotation.x = -ly * 0.012;
    player.position.y = Math.sin(S.time * 24) * 0.014 * clamp(speedPercent(), 0, 1);
    playerGlow.material.opacity = 0.55 + 0.35 * Math.abs(Math.sin(S.time * 3.4));

    roadUni.uTravel.value = S.travel;
    groundUni.uTravel.value = S.travel;
  }

  function updateProps() {
    // Neon direkler
    var base = Math.floor(S.travel / POST_STEP) * POST_STEP;
    for (var i = 0; i < POST_N; i++) {
      var d = base + i * POST_STEP;
      var side = (Math.round(d / POST_STEP) % 2 === 0) ? -1 : 1;
      var x = roadX(d, S.travel) + side * (RH + 2.5);
      var y = roadY(d, S.travel);
      var z = -(d - S.travel);
      var m = makeMatrix(x, y, z, 0, 1);
      posts.setMatrixAt(i, m);
      postTops.setMatrixAt(i, m);
    }
    posts.instanceMatrix.needsUpdate = true;
    postTops.instanceMatrix.needsUpdate = true;

    // Palmiyeler
    var pbase = Math.floor(S.travel / PALM_STEP) * PALM_STEP;
    for (var j = 0; j < PALM_N; j++) {
      var pd = pbase + j * PALM_STEP + 18;
      var pside = (Math.round(pd / PALM_STEP) % 2 === 0) ? -1 : 1;
      var px = roadX(pd, S.travel) + pside * (RH + 8.5);
      var py = roadY(pd, S.travel);
      var pz = -(pd - S.travel);
      var s = 0.85 + ((Math.round(pd / PALM_STEP) % 3) * 0.16);
      var pm = makeMatrix(px, py, pz, pside * 0.4, s);
      palms.setMatrixAt(j, pm);
      fronds.setMatrixAt(j, pm);
    }
    palms.instanceMatrix.needsUpdate = true;
    fronds.instanceMatrix.needsUpdate = true;

    // Tabelalar
    var sbase = Math.floor(S.travel / SIGN_STEP) * SIGN_STEP;
    for (var k = 0; k < SIGN_N; k++) {
      var sd = sbase + k * SIGN_STEP;
      var sside = (Math.round(sd / SIGN_STEP) % 2 === 0) ? -1 : 1;
      var sx2 = roadX(sd, S.travel) + sside * (RH + 6.5);
      var sy2 = roadY(sd, S.travel) + 6.2;
      var sz2 = -(sd - S.travel);
      signs.setMatrixAt(k, makeMatrix(sx2, sy2, sz2, 0, 1));
    }
    signs.instanceMatrix.needsUpdate = true;

    // Trafik araçları
    var ia = 0, ib = 0;
    for (var q = 0; q < traffic.length; q++) {
      var t = traffic[q];
      var x = roadX(t.dist, S.travel) + t.lane;
      var y = roadY(t.dist, S.travel);
      var z = -(t.dist - S.travel);
      var mm = makeMatrix(x, y, z, 0, 1);
      if (t.grp === 0) { if (ia < 3) trafficA.setMatrixAt(ia++, mm); }
      else { if (ib < 3) trafficB.setMatrixAt(ib++, mm); }
    }
    trafficA.instanceMatrix.needsUpdate = true;
    trafficB.instanceMatrix.needsUpdate = true;
  }

  function updateParticles(dt) {
    var spd = S.speed;
    var p = streakGeo.attributes.position;
    var arr = p.array;
    for (var i = 0; i < STREAK_N; i++) {
      var s = streakState[i];
      s.z += (spd * 0.9 + 3) * dt;
      if (s.z > 16) {
        s.z = -180 - Math.random() * 60;
        s.x = S.playerX + rnd(-20, 20);
        s.y = rnd(0.5, 11);
      }
      var len = clamp(spd * 0.055, 0.4, 5.2);
      var o = i * 6;
      arr[o] = s.x; arr[o + 1] = s.y; arr[o + 2] = s.z;
      arr[o + 3] = s.x; arr[o + 4] = s.y; arr[o + 5] = s.z + len;
    }
    p.needsUpdate = true;
    streakMat.opacity = 0.1 + 0.5 * clamp(speedPercent(), 0, 1);

    var sp = sparkGeo.attributes.position;
    var sa = sp.array;
    for (var j = 0; j < SPARK_N; j++) {
      var k = sparkState[j];
      if (k.life > 0) {
        k.life -= dt;
        k.vy -= 16 * dt;
        k.x += k.vx * dt; k.y += k.vy * dt; k.z += k.vz * dt;
        if (k.life <= 0) { k.y = -99; }
      }
      var o2 = j * 3;
      sa[o2] = k.x; sa[o2 + 1] = k.y; sa[o2 + 2] = k.z;
    }
    sp.needsUpdate = true;
  }

  function burstSparks(x, y, z) {
    for (var i = 0; i < 26; i++) {
      var k = sparkState[(sparkTick++) % SPARK_N];
      k.x = x + rnd(-0.7, 0.7);
      k.y = y + rnd(-0.2, 0.4);
      k.z = z;
      k.vx = rnd(-7, 7); k.vy = rnd(3, 11); k.vz = rnd(-6, 9);
      k.life = rnd(0.35, 0.8);
    }
  }
  var sparkTick = 0;

  var hudFrame = 0;
  function updateHud(force) {
    hudFrame++;
    if (!force && hudFrame % 6 !== 0) return;
    if (U.dist) U.dist.textContent = String(S.score);
    if (U.spd) U.spd.textContent = String(Math.round(S.speed * 3.6));
    if (U.best && S.best !== S.bestShown) {
      S.bestShown = S.best;
      U.best.textContent = String(S.best);
      if (U.startBest) U.startBest.textContent = String(S.best);
    }
  }

  /* ── 16. döngü / boyutlandırma ────────────────────────────────────────── */
  var active = true, rafId = 0, lastT = 0, frames = 0, fpsFrames = 0, fpsT0 = 0, fpsVal = 0, scoreSent = -2;
  var lastW = 0, lastH = 0, PORTRAIT = false;

  function resize() {
    var w = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var h = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    lastW = w; lastH = h;
    var aspect = w / h;
    PORTRAIT = aspect < 1;
    // Kompozisyon hedefi: YATAY FOV sabit (~78°), dikey FOV en/boy oranından türetilir.
    // Sabit dikey FOV geniş ekranda yolu ekranın tepesine taşıyordu (ölçüm: 1000x533).
    var hfov = 78 * Math.PI / 180;
    var vfov = 2 * Math.atan(Math.tan(hfov / 2) / aspect) * 180 / Math.PI;
    FOV_BASE = clamp(vfov, 36, 96);
    camera.aspect = aspect;
    camera.fov = FOV_BASE;
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    // updateStyle = true: canvas'a satır içi CSS boyutu da yazılır (emülatör/WebView garantisi)
    renderer.setSize(w, h, true);
  }
  function syncSize() {
    var w = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var h = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    if (w !== lastW || h !== lastH) resize();
  }
  window.addEventListener('resize', resize);
  window.addEventListener('orientationchange', function () { setTimeout(resize, 120); });

  function start() { if (rafId) return; lastT = 0; fpsT0 = 0; rafId = requestAnimationFrame(loop); }
  function stopLoop() { if (rafId) { cancelAnimationFrame(rafId); rafId = 0; } }

  function loop(ts) {
    if (!active) { rafId = 0; return; }
    rafId = requestAnimationFrame(loop);
    var t = ts / 1000;
    var dt = lastT ? Math.min(0.05, t - lastT) : 0.016;
    lastT = t;
    syncSize();
    step(dt);
    renderer.render(scene, camera);
    frames++;
    if (!fpsT0) fpsT0 = t;
    fpsFrames++;
    if (t - fpsT0 >= 0.5) {
      fpsVal = fpsFrames / (t - fpsT0);
      fpsFrames = 0; fpsT0 = t;
      if (Q.fps && U.fps) U.fps.textContent = fpsVal.toFixed(0) + ' fps · ' + renderer.info.render.calls + ' dc';
    }
    // Skor akışı: koşarken saniyede bir olay (Kotlin tarafı isterse polling de yapabilir).
    if (S.state === 'running' && S.time - scoreSent >= 1) {
      scoreSent = S.time;
      emit('score', { mesafe: S.score, hiz_kmh: Math.round(S.speed * 3.6), en_iyi: S.best });
    }
    if (Q.selftest && !reportDone && t > 3.0) { reportDone = true; writeReport(); }
    if (frames % 900 === 0) {
      try {
        console.log('outrun calisiyor n=' + frames + ' tri=' + renderer.info.render.triangles +
          ' cizim=' + renderer.info.render.calls + ' hiz=' + Math.round(S.speed * 3.6) + ' mesafe=' + S.score +
          ' durum=' + S.state);
      } catch (e) {}
    }
  }

  // Sahne aktif/pasif (arka planda rAF durur → WebView'de pil/CPU tasarrufu).
  function setSceneActive(v) {
    var on = !!v;
    if (on === active) return;
    active = on;
    if (active) start(); else { stopLoop(); renderer.render(scene, camera); }
  }

  document.addEventListener('visibilitychange', function () {
    setSceneActive(!document.hidden);
    if (document.hidden && S.state === 'running') setPaused(true);
  });

  /* ── 17. kanıt / ölçüm raporu ─────────────────────────────────────────── */
  var reportDone = false;

  function pixelStats() {
    var c = renderer.domElement;
    var w = 96, h = Math.max(40, Math.round(96 * (c.height || 1) / (c.width || 1)));
    var cv = document.createElement('canvas');
    cv.width = w; cv.height = h;
    var ctx = cv.getContext('2d');
    ctx.drawImage(c, 0, 0, w, h);
    var d;
    try { d = ctx.getImageData(0, 0, w, h).data; } catch (e) { return { hata: 'okunamadi' }; }
    var bands = { sky: [0, 0, 0, 0], ufuk: [0, 0, 0, 0], yol: [0, 0, 0, 0] };
    var mag = 0, cyan = 0, warm = 0, total = 0;
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var o = (y * w + x) * 4;
        var r = d[o], g = d[o + 1], b = d[o + 2];
        total++;
        if (r > 140 && b > 90 && r > g + 40) mag++;
        if (b > 130 && g > 100 && r < g - 20) cyan++;
        if (r > 190 && g > 140 && b < r - 40) warm++;
        var bandName = (y < h * 0.38) ? 'sky' : (y < h * 0.52) ? 'ufuk' : 'yol';
        var A = bands[bandName];
        A[0] += r; A[1] += g; A[2] += b; A[3]++;
      }
    }
    function avg(A) { return A[3] ? [Math.round(A[0] / A[3]), Math.round(A[1] / A[3]), Math.round(A[2] / A[3])] : [0, 0, 0]; }
    // yol bandı alt-orta bölge parlaklığı (neon şeritler görünüyor mu)
    var cx = Math.round(w / 2), lum = 0, n = 0;
    for (var yy = Math.round(h * 0.60); yy < h; yy++) {
      for (var xx = cx - 6; xx <= cx + 6; xx++) {
        var oo = (yy * w + xx) * 4;
        lum += (d[oo] + d[oo + 1] + d[oo + 2]) / 3; n++;
      }
    }
    return {
      boyut: [w, h],
      ortalama: { gokyuzu: avg(bands.sky), ufuk: avg(bands.ufuk), yol: avg(bands.yol) },
      neon_mor_px: +(mag / total * 100).toFixed(2),
      neon_turkuaz_px: +(cyan / total * 100).toFixed(2),
      sicak_gunes_px: +(warm / total * 100).toFixed(2),
      yol_orta_parlaklik: +(n ? lum / n : 0).toFixed(1)
    };
  }

  function writeReport() {
    var rep = {
      surum: 'outrun-1',
      three: THREE.REVISION,
      sayfa: location.pathname.split('/').pop(),
      boyut: [renderer.domElement.width, renderer.domElement.height],
      ekran: [window.innerWidth, window.innerHeight],
      portre: PORTRAIT,
      yazilimGL: SOFT_GL,
      durum: S.state,
      mesafe: S.score,
      en_iyi: S.best,
      carpisma: S.crashes,
      hiz_kmh: Math.round(S.speed * 3.6),
      fps_headless: +fpsVal.toFixed(1),
      kare: frames,
      cizim_cagrisi: renderer.info.render.calls,
      ucgen: renderer.info.render.triangles,
      bellek_doku: renderer.info.memory.textures,
      piksel: pixelStats(),
      hatalar: errors.slice(0, 5)
    };
    var pre = el_('report');
    var txt = 'OUTRUN_REPORT ' + json_(rep);
    if (pre) pre.textContent = txt;
    try { document.title = txt; } catch (e) {}
    emit('selftest', rep);
    return rep;
  }

  /* ── 18. dış API (WebView gömme) ──────────────────────────────────────── */
  window.outrun = {
    version: 'outrun-1',
    start: function () { startRun(); },
    pause: function () { setPaused(true); },
    resume: function () { setPaused(false); },
    toggle: function () { togglePause(); },
    reset: function () { resetRun(); showScreen('start'); S.state = 'menu'; },
    setActive: function (v) { setSceneActive(v); },
    isActive: function () { return active; },
    getState: function () {
      return {
        durum: S.state, mesafe: S.score, en_iyi: S.best, hiz_kmh: Math.round(S.speed * 3.6),
        carpisma: S.crashes, serit_x: +S.playerX.toFixed(2), direksiyon: +S.steer.toFixed(2),
        serit_disi: +(S.offT).toFixed(2), fps: +fpsVal.toFixed(1), kare: frames,
        cizim: renderer.info.render.calls, ucgen: renderer.info.render.triangles
      };
    },
    getReport: function () { return writeReport(); },
    // Ölçüm kancaları (test/optimizasyon): simülasyon adımı ve tek kare çizimi.
    _step: function (dt) { step(dt || 1 / 60); },
    _render: function () { renderer.render(scene, camera); },
    // Katman teşhisi (QA): tek katmanı aç/kapat ya da nesneyi döndür (canlı shader testi).
    _katman: function (ad, gorunur) {
      var m = {
        yol: road, zemin: ground, gokyuzu: skyMesh, dag: mountMesh, gunes: sunMesh,
        sutun: posts, sutun_tepe: postTops, palmiye: palms, yaprak: fronds, tabela: signs,
        trafik_a: trafficA, trafik_b: trafficB, oyuncu: player, cizgiler: streaks, kivilcim: sparks
      }[ad];
      if (!m) return null;
      if (gorunur === undefined) return m;
      m.visible = !!gorunur;
      return m.visible;
    },
    ping: function () { return 'pong'; }
  };

  /* ── 19. önyükleme ────────────────────────────────────────────────────── */
  resize();
  resetRun(true);
  updateHud(true);
  S.best = parseInt(store.get(BEST_KEY) || '0', 10) || 0;
  S.bestShown = -1;
  updateHud(true);

  if (Q.auto || Q.advance > 0) {
    startRun(true);
    if (Q.advance > 0) {
      var n = Math.round(Q.advance * 60);
      for (var i = 0; i < n; i++) step(1 / 60);
    }
  } else {
    S.state = 'menu';
    showScreen('start');
  }

  // Döngüden önce bir kare çiz (ilk kare garantisi). Instanced matrisler ve kamera
  // durumu mutlaka önce kurulmalı — yoksa ilk karede tüm süsler orijinde üst üste.
  updateCamera(1 / 60);
  updateProps();
  updateParticles(1 / 60);
  renderer.render(scene, camera);
  frames++;

  if (Q.freeze) {
    active = false;           // tek kare modu: ekran görüntüsü sabit kalsın
    writeReport();            // kanıt: dondurulmuş karenin piksel/draw-call raporu
    emit('ready', 'freeze:' + S.state + ':' + S.score);
  } else {
    start();
    emit('ready', 'outrun-1 yazilimGL=' + (SOFT_GL ? 1 : 0) + ' adim=' + Math.round(Q.advance * 60));
  }
  emit('boot', 'outrun-1');
})();
