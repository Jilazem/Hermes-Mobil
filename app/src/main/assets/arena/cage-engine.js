/*
 * KAFES DÖVÜŞÜ MOTORU — saf, deterministik, DOM'suz (Tur-20).
 *
 * Simge dili arena3d ile aynı: bot = kutu gövdeli vizörlü robot, görev durumu =
 * dövüş eylemi. Rastgelelik YOK: her şey FNV-1a hash'li LCG'den türer — aynı
 * görev verisi = aynı sahne (test edilebilirlik, tur15 dersi).
 *
 * Durum makinesi (her dövüşçü):
 *   enter (ringe giriş) → idle (nefes/dans) → engage (yaklaş) → jab/hook (saldırı)
 *   → hit/whiff (vuruş/boş) → stagger (sendeleme, kritik başarısızlık)
 *   → victory (görev tamam) | down (rakip kazanınca yığılma)
 *
 * API:
 *   var eng = createEngine();            // deterministik başlangıç
 *   eng.setData({phase, figures});       // Kotlin köprüsüyle aynı şema
 *   eng.tick(dt);                        // sabit dt önerilir (1/60)
 *   eng.snapshot();                      // çizerin okuduğu kopya
 *   eng.drainEvents();                   // biriken olayları ver ve temizle
 *
 * UMD: tarayıcıda window.CageEngine, node'da module.exports (birim testleri).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) { module.exports = factory(); }
  else { root.CageEngine = factory(); }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  /* ── deterministik yardımcılar ─────────────────────────────────────────── */
  function fnv1a(s) {
    var h = 0x811c9dc5;
    for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
    return h >>> 0;
  }
  function lcg(seed) {
    var s = seed >>> 0;
    return function () { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
  }
  function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

  /* ── sabitler (çizer de aynılarını kullanır) ───────────────────────────── */
  var S = {
    RING_HALF: 2.6,        // ring yarı-genişliği (birim)
    ARENA_HALF: 4.2,       // dövüşcü x sınırı (p halı kenarı)
    START_GAP: 1.7,        // dövüşçülerin nötr mesafesi (merkezden)
    MIN_SEP: 0.95,         // iki dövüşcü arası asgari ayrım (üst üste binme yok)
    ENTER_T: 1.4,          // giriş animasyonu süresi (sn)
    ATTACK_MIN: 0.55,      // saldırı aralığı alt sınırı (sn)
    ATTACK_MAX: 1.10,      // saldırı aralığı üst sınırı (sn)
    REACH: 2.05,           // menzil (iki dövüşçü arası mesafe)
    JAB_T: 0.24,           // düz yumruk süresi
    HOOK_T: 0.38,          // kanca süresi
    STAGGER_T: 0.85,       // sendeleme süresi
    COMBO_TTL: 1.30,       // kombo sayacının sıfırlanma süresi
    ADVANCE_SPEED: 0.9,    // yaklaşma hızı (birim/sn)
    RETREAT_SPEED: 0.7,    // geri döngü hızı
    JAB_DMG: [4, 9],       // hasar aralığı [min, maks)
    HOOK_DMG: [6, 13],
    STAGGER_DMG: 6,        // hata anında can kaybı
    WHIFF_P: 0.12,         // saldırı boş geçme olasılığı (kritik başarısızlık)
    BLOCK_P: 0.28,         // çalışmayan dövüşçünün gard alma olasılığı
    MAX_HP: 100,
    KICKOFF_MIN: 0.35,     // etkileşim gecikmesi (giriş sonrası ilk saldırı)
  };

  /* ── dövüşcü ─────────────────────────────────────────────────────────── */
  // side = BAKIŞ yönü: sol dövüşcü +1 (sağa bakar), sağ dövüşcü -1.
  // Nötr yuva: -side * START_GAP (sol -1.7, sağ +1.7); giriş: -side * 6.5.
  function makeFighter(id, name, dir, seedBase) {
    var rng = lcg(fnv1a(id + '|' + name) ^ seedBase);
    return {
      id: id, name: name, side: dir,
      x: dir * -6.5,
      hp: S.MAX_HP,
      pose: 'enter', poseT: 0,
      working: false, done: false, errored: false,
      combo: 0, comboT: 0,
      attackT: S.KICKOFF_MIN + rng() * 0.5,
      hitFlash: 0, shakeT: 0,
      bobSeed: rng(),                           // nefes fazı — statik kare yok
      guard: 0,
      victoryHop: 0,
      _rng: rng,
    };
  }

  /* ── motor ─────────────────────────────────────────────────────────────── */
  function createEngine(seed) {
    var seedBase = (seed >>> 0) || 0xC0FFEE;
    var rng = lcg(seedBase);
    var st = {
      t: 0,
      phase: 'idle',
      fighters: [],
      spectators: [],
      winnerId: null,
      confettiAt: -1,
      crowdPulse: 0,        // alkış dalgası genliği (vuruşla artar, söner)
      round: 1,
    };
    var events = [];

    function emit(e) { e.at = st.t; events.push(e); }

    function figState(f) {
      if (f.errored) return 'error';
      if (f.done) return 'done';
      if (f.working) return 'working';
      return 'waiting';
    }

    /* setData — Kotlin'den gelen şema: {phase, figures:[{id,name,state,badge}]} */
    function setData(data) {
      if (!data || !Array.isArray(data.figures)) return;
      var phase = String(data.phase || 'idle');
      st.phase = phase;
      var figs = data.figures;

      // İki dövüşçü kuralı: önce ÇALIŞANlar, yoksa listeden ilk ikisi;
      // kalanlar tribüne (izleyici) gider — uydurma dövüşçü YOK.
      var order = figs.slice();
      order.sort(function (a, b) {
        return (a.state === 'working' ? 0 : 1) - (b.state === 'working' ? 0 : 1);
      });
      var contenders = order.slice(0, 2);
      var spec = order.slice(2);
      st.spectators = spec.map(function (f) { return { id: f.id, name: f.name, state: f.state }; });

      // Eşleştir / doğur — kimlik kararlı (tur9 dersi: aynı id aynı figür).
      var byId = {};
      st.fighters.forEach(function (f) { byId[f.id] = f; });
      var next = [];
      contenders.forEach(function (cf, i) {
        var f = byId[cf.id];
        if (!f) f = makeFighter(cf.id, cf.name, i === 0 ? 1 : -1, seedBase);
        f.side = i === 0 ? 1 : -1;
        f.working = cf.state === 'working';
        f.done = cf.state === 'done';
        var wasError = f.errored;
        f.errored = cf.state === 'error';
        // Kenar: error'a yeni geçiş → sendeleme + hasar (görev adımı düştü sembolü).
        if (f.errored && !wasError) {
          f.pose = 'stagger'; f.poseT = 0;
          f.hp = Math.max(0, f.hp - S.STAGGER_DMG);
          f.shakeT = 0.4;
          emit({ type: 'stagger', id: f.id });
        }
        next.push(f);
      });
      st.fighters = next;

      // Faz geçişleri
      var anyDone = next.some(function (f) { return f.done; });
      if (anyDone && st.winnerId === null) {
        st.winnerId = next.find(function (f) { return f.done; }).id;
        st.confettiAt = st.t;
        emit({ type: 'victory', id: st.winnerId });
        emit({ type: 'confetti' });
      }
      if (phase === 'stopped' || phase === 'idle') {
        // "Durdur": dövüş donmaz — her iki dövüşçü nefes almaya döner (tur9).
        st.winnerId = null;
        next.forEach(function (f) {
          f.working = false; f.done = false; f.errored = false;
          if (f.pose !== 'enter' && f.pose !== 'retreat') { f.pose = 'idle'; f.poseT = 0; }
        });
      }
    }

    // yaklaşma yönü: rakibin tarafı; rakip yoksa / üst üste ise kendi bakış yönü.
    function dirOf(f, opp) {
      if (!opp) return f.side;
      var d = Math.sign(opp.x - f.x);
      return d !== 0 ? d : f.side;
    }

    /* tek dövüşcü durum makinesi */
    function stepFighter(f, opp, dt) {
      f.poseT += dt;
      f.hitFlash = Math.max(0, f.hitFlash - dt * 3);
      f.shakeT = Math.max(0, f.shakeT - dt * 2);
      if (f.comboT > 0) { f.comboT -= dt; if (f.comboT <= 0) f.combo = 0; }

      var homeX = -f.side * S.START_GAP;
      var toHome = homeX - f.x;

      switch (f.pose) {
        case 'enter':
          // ringe yürüyüş (hızla gir, yumuşak dur)
          f.x += (-f.side * S.START_GAP - f.x) * Math.min(1, dt * 2.6);
          if (f.poseT >= S.ENTER_T) { f.pose = 'idle'; f.poseT = 0; emit({ type: 'entered', id: f.id }); }
          return;

        case 'jab':
        case 'hook':
          var dur = f.pose === 'jab' ? S.JAB_T : S.HOOK_T;
          var half = dur * 0.5;
          // ileri hamle RAKİBE doğru (vuruş anında en uçta) — dinamik yön
          var k = f.poseT < half ? f.poseT / half : 1 - (f.poseT - half) / half;
          f.x += dirOf(f, opp) * k * 1.6 * dt * 6;
          if (f.poseT >= half && !f._hitDone) {
            f._hitDone = true;
            landHit(f, opp, f.pose);
          }
          if (f.poseT >= dur) { f.pose = 'idle'; f.poseT = 0; f._hitDone = false; }
          return;

        case 'stagger':
          f.x += Math.sin(f.poseT * 22 + f.bobSeed * 6) * 0.10 * dt * 6;
          if (f.poseT >= S.STAGGER_T) { f.pose = 'idle'; f.poseT = 0; }
          return;

        case 'victory':
          f.victoryHop = Math.abs(Math.sin(f.poseT * 5.2)) * 0.22;
          return;

        case 'down':
          return;
      }

      // idle / engage davranışı
      var fighting = st.phase === 'running';
      var oppBusy = opp && (opp.working || opp.pose === 'jab' || opp.pose === 'hook');

      if (f.done && st.phase === 'done') { f.pose = 'victory'; f.poseT = 0.01; return; }
      if (st.winnerId && st.winnerId !== f.id && st.phase === 'done') {
        f.pose = 'down'; f.poseT = 0.01; return;
      }

      if (!f.working || !fighting) {
        // çalışmayan: nötr köşesine döner, nefes alır, bazen gard indirir
        f.x += Math.sign(toHome) * Math.min(Math.abs(toHome), S.RETREAT_SPEED * dt);
        f.guard = Math.max(0, f.guard - dt);
        return;
      }

      // çalışan: menzile yaklaş / saldır (yön daima rakibe doğru — geçişme yok)
      var gap = opp ? Math.abs(opp.x - f.x) : 99;
      if (gap > S.REACH) {
        f.x += dirOf(f, opp) * Math.min(gap - S.REACH + 0.05, S.ADVANCE_SPEED * dt);
      }
      f.attackT -= dt;
      if (f.attackT <= 0 && gap <= S.REACH + 0.02) {
        f.pose = rng() < 0.62 ? 'jab' : 'hook';
        f.poseT = 0; f._hitDone = false;
        f.attackT = S.ATTACK_MIN + rng() * (S.ATTACK_MAX - S.ATTACK_MIN);
      }
      // rakip saldırıyorsa bazen gard al (çalışmayan daha sık)
      if (oppBusy && f.guard <= 0 && rng() < 0.05) f.guard = 1;
    }

    /* vuruş anı — hasar, kombo, olaylar (hepsi deterministik rng) */
    function landHit(att, opp, kind) {
      if (!opp || opp.pose === 'down') return;
      if (opp.pose === 'stagger' || opp.errored) {
        // savunmasız: hasar %50 artar
        applyHit(att, opp, kind, 1.5);
        return;
      }
      var whiff = rng() < S.WHIFF_P;
      if (whiff) {
        att.pose = 'stagger'; att.poseT = 0; att.shakeT = 0.35;
        emit({ type: 'whiff', id: att.id, kind: kind });
        return;
      }
      var blocked = opp.guard > 0 && rng() < S.BLOCK_P + 0.4; // guard açıkken blok neredeyse kesin
      if (blocked) {
        emit({ type: 'block', id: opp.id, from: att.id, kind: kind });
        return;
      }
      applyHit(att, opp, kind, 1);
    }

    function applyHit(att, opp, kind, mul) {
      var range = kind === 'jab' ? S.JAB_DMG : S.HOOK_DMG;
      var dmg = Math.round((range[0] + rng() * (range[1] - range[0])) * mul);
      opp.hp = Math.max(0, opp.hp - dmg);
      opp.hitFlash = 1;
      opp.shakeT = 0.25;
      att.combo += 1;
      att.comboT = S.COMBO_TTL;
      st.crowdPulse = Math.min(1, st.crowdPulse + 0.5);
      emit({ type: 'hit', id: att.id, target: opp.id, dmg: dmg, kind: kind, combo: att.combo, hp: opp.hp });
    }

    /* global tick */
    function tick(dt) {
      dt = (typeof dt === 'number' && isFinite(dt)) ? clamp(dt, 0, 0.1) : 0;
      st.t += dt;
      st.crowdPulse = Math.max(0, st.crowdPulse - dt * 0.6);
      var a = st.fighters[0], b = st.fighters[1];
      if (a) stepFighter(a, b || null, dt);
      if (b) stepFighter(b, a || null, dt);
      // arena sınırı: dövüşçüler ringden taşamaz (giriş -6.5'ten lerp ile, sınırsız)
      st.fighters.forEach(function (f) {
        if (f.pose !== 'enter') f.x = clamp(f.x, -S.ARENA_HALF, S.ARENA_HALF);
      });
      // ayrılma kısıtı: dövüşçüler üst üste binemez / birbirini geçemez
      if (a && b) {
        var d = b.x - a.x;
        if (Math.abs(d) < S.MIN_SEP) {
          var mid = (a.x + b.x) / 2;
          // a solda kalacak şekilde dışa it (kim daha soldaysa o sola)
          var left = a.x <= b.x ? a : b;
          var right = left === a ? b : a;
          left.x = mid - S.MIN_SEP / 2;
          right.x = mid + S.MIN_SEP / 2;
        }
      }
    }

    /* snapshot — çizer bu kopyayı okur (motor durumuna dokunmaz) */
    function snapshot() {
      return {
        t: st.t,
        phase: st.phase,
        winnerId: st.winnerId,
        confettiT: st.confettiAt < 0 ? -1 : st.t - st.confettiAt,
        crowdPulse: st.crowdPulse,
        spectators: st.spectators.map(function (s) { return { id: s.id, name: s.name, state: s.state }; }),
        fighters: st.fighters.map(function (f) {
          return {
            id: f.id, name: f.name, side: f.side, x: f.x, hp: f.hp,
            pose: f.pose, poseT: f.poseT,
            combo: f.combo, comboT: f.comboT > 0,
            hitFlash: f.hitFlash, shakeT: f.shakeT, guard: f.guard,
            bobSeed: f.bobSeed, victoryHop: f.victoryHop,
            working: f.working, done: f.done, errored: f.errored,
            state: figState(f),
          };
        }),
      };
    }

    function drainEvents() { var e = events.slice(); events.length = 0; return e; }

    return {
      version: 'cage-1',
      CONST: S,
      setData: setData,
      tick: tick,
      snapshot: snapshot,
      drainEvents: drainEvents,
      _rngSeed: seedBase,
    };
  }

  return { createEngine: createEngine, fnv1a: fnv1a, CONST: S };
});
