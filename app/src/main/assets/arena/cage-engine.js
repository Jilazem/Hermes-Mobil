/* Local debate choreography. Moves acknowledge real stream/tool events;
 * completion celebrates each bot and never invents a winner or debate score. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.CageEngine = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';
  function fnv1a(s) {
    var h = 0x811c9dc5;
    for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
    return h >>> 0;
  }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function key(f) { return String(f.id || '').replace(/#(?:r\d+|synth)$/, ''); }
  var S = { START_GAP: 1.45, MIN_SEP: 1.5, ENTER_T: 1.15, REACH: 3.0,
    JAB_T: 0.42, HOOK_T: 0.56, STAGGER_T: 0.85, MAX_HP: 100, ARENA_HALF: 4.2 };
  function createEngine(seed) {
    var st = { t: 0, phase: 'idle', fighters: [], spectators: [], winnerId: null,
      confettiAt: -1, crowdPulse: 0, round: 1 };
    var events = [];
    function emit(e) { e.at = st.t; events.push(e); }
    function make(f, side) {
      return { key: key(f), id: f.id, name: f.name, side: side, x: -side * 5,
        hp: 100, pose: 'enter', poseT: 0, working: false, done: false, errored: false,
        combo: 0, comboT: 0, hitFlash: 0, shakeT: 0, guard: 0, victoryHop: 0,
        bobSeed: (fnv1a(key(f)) ^ (seed || 0xC0FFEE)) / 4294967296,
        activityVersion: 0, excerpt: '', badge: '', pending: 0, move: 0, cooldown: 0 };
    }
    function setData(data) {
      if (!data || !Array.isArray(data.figures)) return;
      var phase = String(data.phase || 'idle');
      var restart = phase === 'running' && (st.phase === 'done' || st.phase === 'stopped');
      if (restart) { st.fighters = []; st.confettiAt = -1; }
      var latest = [], index = Object.create(null);
      data.figures.forEach(function (f) {
        if (!f || !f.id) return;
        var k = key(f);
        // One character per bot; the latest round/synthesis replaces its old card.
        if (index[k] == null) { index[k] = latest.length; latest.push(f); }
        else latest[index[k]] = f;
      });
      var byKey = Object.create(null);
      st.fighters.forEach(function (f) { byKey[f.key] = f; });
      st.fighters = latest.slice(0, 2).map(function (cf, i) {
        var f = byKey[key(cf)] || make(cf, i === 0 ? 1 : -1);
        var newRound = f.id !== cf.id;
        var wasWorking = f.working, wasDone = f.done, wasError = f.errored;
        var version = Number(cf.activityVersion) || 0;
        if (newRound) { f.pending = 0; f.activityVersion = 0; }
        f.id = cf.id; f.name = cf.name; f.badge = cf.badge || '';
        f.excerpt = cf.excerpt || '';
        f.working = cf.state === 'working'; f.done = cf.state === 'done'; f.errored = cf.state === 'error';
        if (phase === 'running' && f.working) {
          if (!wasWorking || newRound) { f.pending = Math.min(4, f.pending + 1); emit({ type: 'turn', id: f.id }); }
          if (version > f.activityVersion) f.pending = Math.min(4, f.pending + version - f.activityVersion);
        }
        f.activityVersion = version;
        if (f.done && (!wasDone || newRound)) {
          f.pending = 0; f.comboT = 0; emit({ type: 'completed', id: f.id });
        }
        if (f.errored && !wasError) {
          f.pending = 0; f.pose = 'stagger'; f.poseT = 0; f.shakeT = 0.25;
          emit({ type: 'stagger', id: f.id });
        }
        if (phase === 'stopped' || phase === 'idle' || phase === 'ready') {
          f.pending = 0; f.working = false;
          if (f.pose !== 'enter') { f.pose = 'idle'; f.poseT = 0; }
        }
        return f;
      });
      st.spectators = latest.slice(2).map(function (f) {
        return { id: f.id, name: f.name, state: f.state, badge: f.badge, excerpt: f.excerpt || '' };
      });
      if (phase === 'done' && st.phase !== 'done' && latest.some(function (f) { return f.state === 'done'; })) {
        st.confettiAt = st.t; emit({ type: 'confetti' });
      }
      if (phase === 'stopped' || phase === 'idle' || phase === 'ready') st.confettiAt = -1;
      st.phase = phase;
    }
    function step(f, opp, dt) {
      f.poseT += dt; f.cooldown = Math.max(0, f.cooldown - dt);
      f.hitFlash = Math.max(0, f.hitFlash - dt * 3); f.shakeT = Math.max(0, f.shakeT - dt);
      f.comboT = Math.max(0, f.comboT - dt); if (!f.comboT) f.combo = 0;
      var home = -f.side * S.START_GAP;
      if (f.pose === 'enter') {
        f.x += (home - f.x) * Math.min(1, dt * 4);
        if (f.poseT >= S.ENTER_T) { f.pose = 'idle'; f.poseT = 0; emit({ type: 'entered', id: f.id }); }
        return;
      }
      if (f.pose === 'stagger') {
        if (f.poseT >= S.STAGGER_T) { f.pose = 'idle'; f.poseT = 0; }
        return;
      }
      if (f.pose === 'jab' || f.pose === 'hook') {
        var duration = f.pose === 'jab' ? S.JAB_T : S.HOOK_T;
        if (f.poseT >= duration / 2 && !f.hitSent) {
          f.hitSent = true; f.combo++; f.comboT = 1.8; st.crowdPulse = 1;
          if (opp) { opp.hitFlash = 0.35; opp.guard = 0.5; }
          emit({ type: 'hit', id: f.id, target: opp && opp.id, combo: f.combo, kind: f.pose,
            source: 'debate-activity', activityVersion: f.activityVersion });
        }
        if (f.poseT >= duration) { f.pose = 'idle'; f.poseT = 0; f.cooldown = 0.35; }
        return;
      }
      if (st.phase === 'done' && f.done) {
        if (f.pose !== 'victory') { f.pose = 'victory'; f.poseT = 0; }
        f.victoryHop = Math.abs(Math.sin(f.poseT * 5.2)) * 0.12; return;
      }
      f.pose = 'idle'; f.victoryHop = 0; f.guard = Math.max(0, f.guard - dt);
      f.x += (home + Math.sin(st.t * 2.2 + f.bobSeed * 6) * 0.045 - f.x) * Math.min(1, dt * 3);
      if (st.phase === 'running' && f.working && f.pending && !f.cooldown) {
        f.pending--; f.move++; f.pose = f.move % 3 === 0 ? 'hook' : 'jab'; f.poseT = 0; f.hitSent = false;
      }
    }
    function tick(dt) {
      dt = typeof dt === 'number' && isFinite(dt) ? clamp(dt, 0, 0.1) : 0;
      st.t += dt; st.crowdPulse = Math.max(0, st.crowdPulse - dt * 0.8);
      st.fighters.forEach(function (f, i) { step(f, st.fighters[1 - i], dt); });
    }
    function snapshot() {
      return { t: st.t, phase: st.phase, winnerId: null,
        confettiT: st.confettiAt < 0 ? -1 : st.t - st.confettiAt, crowdPulse: st.crowdPulse,
        spectators: st.spectators.map(function (f) { return Object.assign({}, f); }),
        fighters: st.fighters.map(function (f) {
          return Object.assign({}, f, { state: f.errored ? 'error' : f.done ? 'done' : f.working ? 'working' : 'waiting', comboT: f.comboT > 0 });
        }) };
    }
    return { version: 'cage-2', CONST: S, setData: setData, tick: tick, snapshot: snapshot,
      drainEvents: function () { var e = events.slice(); events.length = 0; return e; } };
  }
  return { createEngine: createEngine, fnv1a: fnv1a, CONST: S };
});
