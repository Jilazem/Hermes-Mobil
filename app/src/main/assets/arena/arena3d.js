/*
 * Arena 3D sahnesi — three.js r147 (uygulama asset'i, MIT).
 *
 * Veri Kotlin'den gelir: window.arenaScene.setData('<json>')
 *   { "phase":"idle|ready|running|done|stopped",
 *     "theme": { "bg":"#0b0f13", "accent":"#5ec8ff", "ok":"#4ade80", "danger":"#ff4d5e" },
 *     "figures":[ { "id":"alfa#1", "name":"alfa", "state":"waiting|working|done|error", "badge":"Tur 2" } ] }
 *
 * Olaylar Kotlin'e döner: __ArenaBridge.onSceneEvent(type, detail)
 *   ready | nowebgl | error | data
 *
 * Kural: çalışma anında ağ erişimi YOK, yalnız yerel asset. Etiketler her fazda okunur kalır.
 */
(function () {
  'use strict';

  var bridge = window.__ArenaBridge || null;

  function emit(type, detail) {
    var d = (detail == null) ? '' : String(detail);
    try { if (bridge && bridge.onSceneEvent) bridge.onSceneEvent(type, d); } catch (e) { /* yutulmaz: konsola düşer */ }
    try { console.log('arena3d ' + type + ' ' + d); } catch (e2) {}
  }

  function showFallback(msg) {
    var el = document.getElementById('fallback');
    if (el) { el.style.display = 'flex'; if (msg) { el.textContent = msg; } }
  }

  function hasWebGL() {
    try {
      var c = document.createElement('canvas');
      return !!(window.WebGLRenderingContext && (c.getContext('webgl') || c.getContext('experimental-webgl')));
    } catch (e) { return false; }
  }

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

  var STAGE = document.getElementById('stage');

  // Yazılım GL (SwiftShader/llvmpipe) tespiti: çok örnekli framebuffer bu ortamda
  // sunulamıyor → antialias kapatılır (kare siyah kalıyordu). Gerçek cihazda AA açık.
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
  var SOFT_GL = detectSoftGL();

  // ── palet (Kotlin'den ezilebilir) ────────────────────────────────────────
  var THEME = {
    bg: '#0b0f13',
    grid: '#1b2a36',
    grid2: '#111b23',
    accent: '#5ec8ff',
    ok: '#4ade80',
    danger: '#ff4d5e'
  };

  function hex2int(hex, fallback) {
    if (typeof hex !== 'string') return fallback;
    var h = hex.replace('#', '');
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    var v = parseInt(h, 16);
    return isNaN(v) ? fallback : v;
  }

  // ── durum sözlüğü ────────────────────────────────────────────────────────
  var PHASE_ACCENT = {
    idle: 0x4a6b80,
    ready: 0x7fb4d8,
    running: 0x5ec8ff,
    done: 0x4ade80,
    stopped: 0x6b8ba0
  };

  // Yazılım GL'de (emülatör) iki ayar zorunlu: AA kapalı + preserveDrawingBuffer açık
  // — aksi hâlde WebView kareyi sunmuyor (tur-9 ölçümü: 814 üçgen çiziliyor, ekran boş).
  var renderer = new THREE.WebGLRenderer({
    antialias: !SOFT_GL,
    preserveDrawingBuffer: SOFT_GL,
    powerPreference: 'low-power',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); // pixelRatio <= 2
  renderer.setSize(window.innerWidth, window.innerHeight, false);
  renderer.outputEncoding = THREE.sRGBEncoding; // doğru renk/parlaklık (r147 varsayılanı lineer)
  renderer.setClearColor(hex2int(THEME.bg, 0x0b0f13), 1);
  STAGE.appendChild(renderer.domElement);

  var scene = new THREE.Scene();
  scene.background = new THREE.Color(hex2int(THEME.bg, 0x0b0f13));
  scene.fog = new THREE.Fog(hex2int(THEME.bg, 0x0b0f13), 13, 40);

  var camera = new THREE.PerspectiveCamera(48, window.innerWidth / Math.max(1, window.innerHeight), 0.1, 140);
  camera.position.set(0, 3.1, 9.8);
  camera.lookAt(0, 1.4, 0);

  var hemi = new THREE.HemisphereLight(0x9fd7ff, 0x080d12, 0.55);
  scene.add(hemi);
  var dirLight = new THREE.DirectionalLight(0xffffff, 0.6);
  dirLight.position.set(4, 9, 6);
  scene.add(dirLight);
  var accentLight = new THREE.PointLight(0x5ec8ff, 1.0, 30, 2);
  accentLight.position.set(0, 4.6, 2.4);
  scene.add(accentLight);

  var grid = new THREE.GridHelper(34, 34, hex2int(THEME.grid, 0x1b2a36), hex2int(THEME.grid2, 0x111b23));
  grid.material.opacity = 0.6;
  grid.material.transparent = true;
  scene.add(grid);

  var platform = new THREE.Mesh(
    new THREE.CircleGeometry(7.6, 48),
    new THREE.MeshStandardMaterial({ color: 0x080d13, metalness: 0.25, roughness: 0.9 })
  );
  platform.rotation.x = -Math.PI / 2;
  scene.add(platform);

  var stageRing = new THREE.Mesh(
    new THREE.RingGeometry(7.0, 7.45, 64),
    new THREE.MeshBasicMaterial({ color: 0x1d3a4d, transparent: true, opacity: 0.75, side: THREE.DoubleSide })
  );
  stageRing.rotation.x = -Math.PI / 2;
  stageRing.position.y = 0.012;
  scene.add(stageRing);

  // ── etiket (billboard sprite, CanvasTexture) ─────────────────────────────
  var LABEL_W = 640, LABEL_H = 200;

  function drawLabel(canvas, name, badge, state, accentColor, badgeColor) {
    var ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, LABEL_W, LABEL_H);

    var hasBadge = !!badge;
    // zemin kart
    var x = 10, y = 26, w = LABEL_W - 20, h = 148;
    ctx.fillStyle = 'rgba(8,13,18,0.92)';
    roundRect(ctx, x, y, w, h, 24);
    ctx.fill();
    ctx.lineWidth = 6;
    ctx.strokeStyle = accentColor;
    ctx.globalAlpha = (state === 'error') ? 0.95 : 0.85;
    roundRect(ctx, x, y, w, h, 24);
    ctx.stroke();
    ctx.globalAlpha = 1;

    // durum noktası
    ctx.beginPath();
    ctx.arc(x + 40, y + 34, 13, 0, Math.PI * 2);
    ctx.fillStyle = accentColor;
    ctx.fill();

    // ad — kutuya sığana kadar küçült (etiket HER fazda okunur kalır)
    var maxTextW = w - 110;
    var size = 62;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    do {
      ctx.font = 'bold ' + size + 'px -apple-system, Roboto, sans-serif';
      if (ctx.measureText(name).width <= maxTextW || size <= 24) break;
      size -= 2;
    } while (true);
    ctx.fillStyle = '#f2f8ff';
    ctx.fillText(name, LABEL_W / 2, hasBadge ? y + 58 : y + h / 2);

    // rozet (Tur 2 / Sentez / Canlı)
    if (hasBadge) {
      ctx.font = 'bold 38px -apple-system, Roboto, sans-serif';
      var bw = ctx.measureText(badge).width + 52;
      var bx = LABEL_W / 2 - bw / 2, by = y + 96, bh = 50;
      ctx.fillStyle = badgeColor;
      roundRect(ctx, bx, by, bw, bh, 14);
      ctx.fill();
      ctx.fillStyle = '#08131b';
      ctx.fillText(badge, LABEL_W / 2, by + bh / 2 + 1);
    }
    return size;
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  // ── efektler (onay parlaması / kesinti halkası) ──────────────────────────
  var effects = [];

  function spawnRing(x, z, colorInt, ttl, fromR, toR) {
    var mesh = new THREE.Mesh(
      new THREE.RingGeometry(0.5, 0.62, 48),
      new THREE.MeshBasicMaterial({ color: colorInt, transparent: true, opacity: 0.9, side: THREE.DoubleSide })
    );
    mesh.rotation.x = -Math.PI / 2;
    mesh.position.set(x, 0.05, z);
    scene.add(mesh);
    effects.push({ mesh: mesh, t: 0, ttl: ttl, from: fromR, to: toR });
  }

  function updateEffects(dt) {
    for (var i = effects.length - 1; i >= 0; i--) {
      var e = effects[i];
      e.t += dt;
      var k = Math.min(1, e.t / e.ttl);
      var s = e.from + (e.to - e.from) * k;
      e.mesh.scale.set(s, s, s);
      e.mesh.material.opacity = 0.9 * (1 - k);
      if (k >= 1) {
        scene.remove(e.mesh);
        e.mesh.geometry.dispose();
        e.mesh.material.dispose();
        effects.splice(i, 1);
      }
    }
  }

  // ── figür: düşük poligonlu gladyatör-bot ─────────────────────────────────
  var BADGE_COLORS = { r1: '#7fb4d8', r2: '#ffb347', synth: '#c58cff', live: '#5ec8ff' };

  function makeFigure(id, name, badge, state) {
    var group = new THREE.Group();

    var bodyMat = new THREE.MeshStandardMaterial({ color: 0x36445a, metalness: 0.55, roughness: 0.42, emissive: 0x0d2130, emissiveIntensity: 0.7 });
    var trimMat = new THREE.MeshStandardMaterial({ color: 0x53657a, metalness: 0.7, roughness: 0.35 });
    var accentMat = new THREE.MeshStandardMaterial({ color: 0x7fb4d8, metalness: 0.35, roughness: 0.4, emissive: 0x1b4a66, emissiveIntensity: 1.0 });
    var coreMat = new THREE.MeshStandardMaterial({ color: 0xdcf6ff, emissive: 0x8ef0ff, emissiveIntensity: 1.2, metalness: 0.2, roughness: 0.3 });
    var ringMat = new THREE.MeshBasicMaterial({ color: 0x5ec8ff, transparent: true, opacity: 0.6 });

    function part(parent, geo, mat, x, y, z) {
      var m = new THREE.Mesh(geo, mat);
      m.position.set(x, y, z);
      parent.add(m);
      return m;
    }

    // gövde
    var torso = part(group, new THREE.BoxGeometry(0.88, 0.6, 0.5), bodyMat, 0, 1.3, 0);
    part(group, new THREE.BoxGeometry(0.62, 0.44, 0.42), bodyMat, 0, 0.88, 0);
    part(group, new THREE.BoxGeometry(0.8, 0.26, 0.46), trimMat, 0, 0.58, 0);
    part(group, new THREE.BoxGeometry(0.98, 0.18, 0.42), trimMat, 0, 1.68, 0); // omuz kuşağı

    // baş
    part(group, new THREE.BoxGeometry(0.48, 0.44, 0.44), bodyMat, 0, 1.98, 0);
    var visorMat = new THREE.MeshStandardMaterial({ color: 0x9fe8ff, emissive: 0x8ef0ff, emissiveIntensity: 1.0 });
    part(group, new THREE.BoxGeometry(0.4, 0.12, 0.06), visorMat, 0, 2.0, 0.23);
    part(group, new THREE.BoxGeometry(0.08, 0.26, 0.36), accentMat, 0, 2.25, -0.03); // miğfer tepesi

    // bacaklar
    part(group, new THREE.BoxGeometry(0.2, 0.6, 0.24), trimMat, -0.21, 0.28, 0);
    part(group, new THREE.BoxGeometry(0.2, 0.6, 0.24), trimMat, 0.21, 0.28, 0);
    part(group, new THREE.BoxGeometry(0.26, 0.12, 0.32), bodyMat, -0.21, 0.06, 0.03);
    part(group, new THREE.BoxGeometry(0.26, 0.12, 0.32), bodyMat, 0.21, 0.06, 0.03);

    // kollar (ayrı grup → animasyon)
    function arm(side) {
      var a = new THREE.Group();
      a.position.set(side * 0.62, 1.55, 0);
      part(a, new THREE.BoxGeometry(0.2, 0.46, 0.22), trimMat, 0, -0.24, 0);
      part(a, new THREE.BoxGeometry(0.17, 0.34, 0.18), bodyMat, 0, -0.6, 0.06);
      part(a, new THREE.BoxGeometry(0.2, 0.14, 0.2), accentMat, 0, -0.8, 0.12);
      group.add(a);
      return a;
    }
    var armL = arm(-1), armR = arm(1);

    // gladyatör kalkanı (sol kol)
    var shield = part(armL, new THREE.CylinderGeometry(0.3, 0.3, 0.06, 12), bodyMat, -0.16, -0.62, 0.12);
    shield.rotation.z = Math.PI / 2;
    part(shield, new THREE.CylinderGeometry(0.1, 0.1, 0.09, 10), accentMat, 0, 0, 0);

    // enerji çekirdeği
    var core = part(group, new THREE.SphereGeometry(0.13, 12, 10), coreMat, 0, 1.34, 0.28);

    // çalışma halkası
    var ring = part(group, new THREE.TorusGeometry(0.78, 0.028, 8, 40), ringMat, 0, 0.34, 0);
    ring.rotation.x = Math.PI / 2;
    ring.visible = false;

    // kıvılcımlar (çalışırken yükselen enerji)
    var sparkCount = 10;
    var sparkPos = new Float32Array(sparkCount * 3);
    for (var i = 0; i < sparkCount; i++) {
      sparkPos[i * 3] = (Math.random() - 0.5) * 0.7;
      sparkPos[i * 3 + 1] = Math.random() * 2.1;
      sparkPos[i * 3 + 2] = (Math.random() - 0.5) * 0.7;
    }
    var sparkGeo = new THREE.BufferGeometry();
    sparkGeo.setAttribute('position', new THREE.BufferAttribute(sparkPos, 3));
    var sparkMat = new THREE.PointsMaterial({ color: 0x8ef0ff, size: 0.09, transparent: true, opacity: 0.95 });
    var sparks = new THREE.Points(sparkGeo, sparkMat);
    sparks.visible = false;
    group.add(sparks);

    // etiket billboard
    var canvas = document.createElement('canvas');
    canvas.width = LABEL_W;
    canvas.height = LABEL_H;
    var tex = new THREE.CanvasTexture(canvas);
    tex.minFilter = THREE.LinearFilter;
    tex.magFilter = THREE.LinearFilter;
    // Etiket: billboard DÜZLEM (kameraya döner, her fazda okunur kalır).
    var labelMat = new THREE.MeshBasicMaterial({
      map: tex, transparent: true, depthTest: false, depthWrite: false, side: THREE.DoubleSide,
    });
    var sprite = new THREE.Mesh(new THREE.PlaneGeometry(3.6, 1.125), labelMat);
    sprite.position.set(0, 3.05, 0);
    sprite.renderOrder = 30; // her fazda okunur kalır (öndeki figür örtmez)
    group.add(sprite);

    var self = {
      id: id,
      group: group,
      order: 0,
      labelName: null,
      labelBadge: '\u0000',
      state: null,
      baseX: 0,
      baseZ: 0,
      spawnK: 0,
      accent: new THREE.Color(0x7fb4d8),
      clock: Math.random() * 10,
      accentMat: accentMat,
      coreMat: coreMat,
      visorMat: visorMat,
      ringMat: ringMat,
      sparkMat: sparkMat,
      sparkGeo: sparkGeo,
      parts: { torso: torso, armL: armL, armR: armR, ring: ring, sparks: sparks, sprite: sprite, shield: shield }
    };

    self.setBase = function (x, z) { self.baseX = x; self.baseZ = z; group.position.x = x; group.position.z = z; };

    self.setLabel = function (n, b) {
      if (n === self.labelName && b === self.labelBadge) return;
      self.labelName = n;
      self.labelBadge = b;
      var st = self.state || 'waiting';
      var accent = '#' + self.accent.getHexString();
      var bu = String(b).toUpperCase();
      // Rozet rengi dile göre metinden çözülür (TR "Tur 2"/"Sentez"/"Canlı", EN "Round 2"/"Synthesis"/"Live").
      var bc = bu.indexOf('TUR 1') >= 0 || bu.indexOf('ROUND 1') >= 0 ? BADGE_COLORS.r1
        : bu.indexOf('TUR 2') >= 0 || bu.indexOf('ROUND 2') >= 0 ? BADGE_COLORS.r2
        : bu.indexOf('SENTEZ') >= 0 || bu.indexOf('SYNTHESIS') >= 0 ? BADGE_COLORS.synth
        : bu.indexOf('CANLI') >= 0 || bu.indexOf('LIVE') >= 0 ? BADGE_COLORS.live
        : '#3d4b5a';
      var secilen = drawLabel(canvas, n, b, st, accent, bc);
      tex.needsUpdate = true;
      console.log('arena3d etiket "' + n + '" rozet=' + (b || '-') + ' durum=' + st +
        ' cerceve=' + accent + ' punto=' + secilen);
    };

    self.setState = function (next) {
      var st = next || 'waiting';
      var prev = self.state;
      self.state = st;
      var map = {
        waiting: 0x7fb4d8,
        working: 0x5ec8ff,
        done: 0x4ade80,
        error: 0xff4d5e
      };
      self.accent.setHex(map[st] != null ? map[st] : 0x7fb4d8);
      accentMat.color.copy(self.accent);
      accentMat.emissive.copy(self.accent).multiplyScalar(0.35);
      coreMat.emissive.copy(self.accent).lerp(new THREE.Color(0xffffff), 0.35);
      visorMat.emissive.copy(self.accent);
      ringMat.color.copy(self.accent);
      sparkMat.color.copy(self.accent);

      // etiketi yeni durum rengiyle tazele
      var keep = self.labelBadge;
      self.labelBadge = '\u0000';
      self.setLabel(self.labelName || '?', keep === '\u0000' ? null : keep);

      if (prev && prev !== st) {
        if (st === 'done') {
          spawnRing(self.baseX, self.baseZ, 0x4ade80, 1.5, 0.5, 4.2);
          spawnRing(self.baseX, self.baseZ, 0x9dffc0, 1.9, 0.4, 5.4);
        } else if (st === 'error') {
          spawnRing(self.baseX, self.baseZ, 0xff4d5e, 0.6, 0.5, 2.6);
        } else if (st === 'working') {
          spawnRing(self.baseX, self.baseZ, 0x5ec8ff, 0.8, 0.4, 2.2);
        }
      }
    };

    self.dispose = function () {
      group.traverse(function (o) {
        if (o.geometry) o.geometry.dispose();
        if (o.material) {
          if (o.material.map) o.material.map.dispose();
          o.material.dispose();
        }
      });
      tex.dispose();
    };

    self.update = function (dt) {
      self.clock += dt;
      var t = self.clock;
      var st = self.state || 'waiting';

      // ortaya çıkış (yeni figür yükselir)
      if (self.spawnK < 1) {
        self.spawnK = Math.min(1, self.spawnK + dt * 1.8);
        group.scale.setScalar(0.35 + 0.65 * self.spawnK);
      } else if (group.scale.x !== 1) {
        group.scale.setScalar(1);
      }

      // nefes / sallanma
      var slow = Math.sin(t * 1.15);
      var breath = slow * 0.5 + 0.5;
      var bob = st === 'working' ? Math.sin(t * 4.4) * 0.055
        : st === 'done' ? Math.sin(t * 2.6) * 0.05
        : Math.sin(t * 1.15) * 0.022;
      group.position.y = bob + (1 - self.spawnK) * -0.4;

      torso.scale.set(1 + breath * 0.012, 1 + breath * 0.02, 1 + breath * 0.012);

      // kollar
      if (st === 'working') {
        armL.rotation.x = -0.55 + Math.sin(t * 11) * 0.5;
        armR.rotation.x = -0.55 + Math.sin(t * 11 + Math.PI) * 0.5;
        armL.rotation.z = 0.16; armR.rotation.z = -0.16;
      } else if (st === 'done') {
        armL.rotation.x = -2.0 + Math.sin(t * 2.2) * 0.12;
        armR.rotation.x = -2.0 + Math.sin(t * 2.2 + 0.6) * 0.12;
        armL.rotation.z = 0.42; armR.rotation.z = -0.42;
      } else if (st === 'error') {
        armL.rotation.x = -0.12 + Math.sin(t * 13) * 0.22;
        armR.rotation.x = -0.12 + Math.sin(t * 13 + 1.7) * 0.22;
        armL.rotation.z = 0.05; armR.rotation.z = -0.05;
      } else {
        armL.rotation.x = Math.sin(t * 1.1) * 0.07;
        armR.rotation.x = Math.sin(t * 1.1 + 1.4) * 0.07;
        armL.rotation.z = 0.05; armR.rotation.z = -0.05;
      }

      // çekirdek + vizör enerjisi
      var pulse = st === 'error' ? (0.5 + Math.abs(Math.sin(t * 12)) * 1.7)
        : st === 'working' ? (1.1 + Math.sin(t * 5.5) * 0.7)
        : st === 'done' ? (1.2 + Math.sin(t * 2.2) * 0.55)
        : (0.45 + breath * 0.4);
      coreMat.emissiveIntensity = pulse;
      core.scale.setScalar(0.9 + pulse * 0.1);
      visorMat.emissiveIntensity = st === 'error' ? (0.4 + Math.abs(Math.sin(t * 14)) * 1.4) : (0.7 + breath * 0.5);

      // çalışma halkası
      if (st === 'working' || st === 'done') {
        ring.visible = true;
        ring.rotation.z += dt * (st === 'working' ? 2.4 : 0.7);
        ring.position.y = 0.34 + Math.sin(t * 2) * 0.05;
        ringMat.opacity = 0.4 + Math.abs(Math.sin(t * (st === 'working' ? 6 : 2))) * 0.5;
      } else {
        ring.visible = false;
      }

      // kıvılcımlar (yalnız çalışırken)
      if (st === 'working') {
        sparks.visible = true;
        var pos = sparkGeo.attributes.position;
        for (var i = 0; i < pos.count; i++) {
          var y = pos.getY(i) + dt * (0.9 + (i % 3) * 0.25);
          if (y > 2.2) { y = 0.05; }
          pos.setY(i, y);
        }
        pos.needsUpdate = true;
      } else {
        sparks.visible = false;
      }

      // hata: kesinti titremesi
      if (st === 'error') {
        group.position.x = self.baseX + Math.sin(t * 26) * 0.045;
        group.rotation.z = Math.sin(t * 21) * 0.035;
      } else {
        group.position.x += (self.baseX - group.position.x) * Math.min(1, dt * 6);
        group.rotation.z += (0 - group.rotation.z) * Math.min(1, dt * 5);
      }

      // etiket her fazda görünür + hafif nefes (billboard: kameraya döner)
      sprite.position.y = 3.05 + Math.sin(t * 1.18) * 0.03;
      sprite.lookAt(camera.position);
      sprite.material.opacity = (st === 'error') ? 0.85 + Math.abs(Math.sin(t * 7)) * 0.15 : 1;
    };

    self.setState(state);
    self.spawnK = 0;
    return self;
  }

  // ── yerleşim ─────────────────────────────────────────────────────────────
  var figures = {};
  var targetCamZ = 9.8;
  var currentPhase = 'idle';

  function list() {
    var out = [];
    for (var k in figures) { out.push(figures[k]); }
    out.sort(function (a, b) { return a.order - b.order; });
    return out;
  }

  function layout() {
    var arr = list();
    var n = arr.length;
    if (n === 0) { targetCamZ = 9.8; return; }
    var spacing = n <= 4 ? 2.35 : Math.max(1.3, 9.0 / (n - 1));
    for (var i = 0; i < n; i++) {
      var off = i - (n - 1) / 2;
      arr[i].setBase(off * spacing, -Math.abs(off) * 0.2);
    }
    targetCamZ = Math.min(16, Math.max(7.4, n * spacing * 0.5 + 4.4));
  }

  function syncFigures(listData) {
    var data = listData || [];
    var seen = {};
    for (var i = 0; i < data.length; i++) {
      var f = data[i] || {};
      var id = String(f.id || f.name || ('f' + i));
      seen[id] = true;
      var h = figures[id];
      if (!h) {
        h = makeFigure(id, String(f.name || id), f.badge ? String(f.badge) : null, f.state || 'waiting');
        figures[id] = h;
        scene.add(h.group);
        spawnRing(0, 0, 0x5ec8ff, 0.7, 0.4, 2.0);
      }
      h.order = (typeof f.order === 'number') ? f.order : i;
      h.setLabel(String(f.name || id), f.badge ? String(f.badge) : null);
      if (h.state !== (f.state || 'waiting')) h.setState(f.state || 'waiting');
      else h.setState(h.state);
    }
    for (var k in figures) {
      if (!seen[k]) {
        scene.remove(figures[k].group);
        figures[k].dispose();
        delete figures[k];
      }
    }
    layout();
  }

  // ── veri köprüsü (Kotlin → JS) ───────────────────────────────────────────
  var lastData = null;

  window.arenaScene = {
    version: 'arena3d-1',
    setData: function (raw) {
      try {
        var d = (typeof raw === 'string') ? JSON.parse(raw) : (raw || {});
        lastData = d;
        if (d.theme) {
          if (d.theme.bg) { scene.background = new THREE.Color(hex2int(d.theme.bg, 0x0b0f13)); scene.fog.color = scene.background; renderer.setClearColor(hex2int(d.theme.bg, 0x0b0f13), 1); }
          if (d.theme.grid) grid.material.color = new THREE.Color(hex2int(d.theme.grid, 0x1b2a36));
        }
        currentPhase = d.phase || 'idle';
        var phaseAccent = PHASE_ACCENT[currentPhase] != null ? PHASE_ACCENT[currentPhase] : 0x5ec8ff;
        if (d.theme && d.theme.accent) phaseAccent = hex2int(d.theme.accent, phaseAccent);
        if (currentPhase === 'done') phaseAccent = 0x4ade80;
        accentLight.color.setHex(phaseAccent);
        stageRing.material.color.setHex(phaseAccent);
        stageRing.material.opacity = currentPhase === 'idle' ? 0.45 : 0.8;
        syncFigures(d.figures);
        emit('data', (d.figures ? d.figures.length : 0) + '|' + currentPhase);
      } catch (e) {
        emit('error', 'veri:' + (e && e.message ? e.message : 'okunamadi'));
      }
    },
    setActive: function (v) {
      var on = !!v;
      if (on === active) return;
      active = on;
      if (active) { renderOnce = true; start(); }
      else { stopLoop(); render(); }
    },
    isActive: function () { return active; },
    figureCount: function () { return list().length; },
    ping: function () { return 'pong'; }
  };

  // ── döngü ────────────────────────────────────────────────────────────────
  var active = true;
  var rafId = 0;
  var lastT = 0;
  var renderOnce = true;
  var announced = false;

  var frames = 0;
  var lastW = 0, lastH = 0;

  function resize() {
    var w = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var h = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    lastW = w; lastH = h;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    // updateStyle = true: canvas'a satır içi CSS boyutu da yazılır (görünürlük garantisi).
    renderer.setSize(w, h, true);
  }
  // Yerleşim geç oturursa (WebView ilk ölçümü 0 olabiliyor) kare başına denetle.
  function syncSize() {
    var w = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var h = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    if (w !== lastW || h !== lastH) resize();
  }
  window.addEventListener('resize', resize);
  window.addEventListener('orientationchange', resize);

  function render() {
    renderer.render(scene, camera);
  }

  function start() {
    if (rafId) return;
    lastT = 0;
    rafId = requestAnimationFrame(loop);
  }

  function stopLoop() {
    if (rafId) { cancelAnimationFrame(rafId); rafId = 0; }
  }

  function loop(ts) {
    if (!active) { rafId = 0; return; }
    rafId = requestAnimationFrame(loop);
    var t = ts / 1000;
    var dt = lastT ? Math.min(0.05, t - lastT) : 0.016;
    lastT = t;

    syncSize();

    var arr = list();
    for (var i = 0; i < arr.length; i++) { arr[i].update(dt); }
    updateEffects(dt);

    // kamera: hafif salınım + figür sayısına göre mesafe
    camera.position.z += (targetCamZ - camera.position.z) * Math.min(1, dt * 1.4);
    camera.position.x = Math.sin(t * 0.16) * 1.5;
    camera.position.y = 3.1 + Math.sin(t * 0.23) * 0.15;
    camera.lookAt(0, 1.4, 0);

    if (currentPhase === 'running') { stageRing.material.opacity = 0.65 + Math.abs(Math.sin(t * 2.2)) * 0.3; }
    dirLight.position.x = 4 + Math.sin(t * 0.2) * 1.2;

    render();

    frames++;
    if (frames % 600 === 0) {
      console.log('arena3d calisiyor n=' + frames + ' tri=' + renderer.info.render.triangles +
        ' cizim=' + renderer.info.render.calls + ' figur=' + arr.length + ' aktif=' + active);
    }

    if (renderOnce) {
      renderOnce = false;
      if (!announced) {
        announced = true;
        emit('ready', 'figur:' + arr.length + ' yazilimGL=' + (SOFT_GL ? 1 : 0));
      }
    }
  }

  document.addEventListener('visibilitychange', function () {
    window.arenaScene.setActive(!document.hidden);
  });

  resize();
  setActiveStatus();
  function setActiveStatus() {
    // Sahne hazır: veri gelene kadar boş arena çizilir.
    start();
    emit('boot', 'arena3d-1');
  }
})();
