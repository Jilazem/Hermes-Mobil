// Tur-20 r3 determinism probu (denetim r2/MEDIUM istegi: "ayri kosu kaydet").
// engine_test.js'ten BAGIMSIZ tek seferlik probe. API: createEngine(seed) +
// setData(phase,figures) + tick(dt) + snapshot().
// Kayit: denetim/tur20/kanit/determinism-probe.txt (node ile bu dosya kosulur).
const path = require('path');
const engine = require(path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'arena', 'cage-engine.js'));

function twoFighters(e) {
  e.setData({
    phase: 'running',
    figures: [
      { id: 'coder#r1', name: 'coder', state: 'working', badge: 'Tur 1' },
      { id: 'android#r1', name: 'android', state: 'working', badge: 'Tur 1' },
    ],
  });
}
function run(e, secs) { for (let i = 0; i < Math.round(secs * 60); i++) e.tick(1 / 60); }

// 1) seed=123, 60sn, iki AYRI motor → bit-bit ayni snapshot
const a = engine.createEngine(123); twoFighters(a); run(a, 60);
const b = engine.createEngine(123); twoFighters(b); run(b, 60);
const sa = JSON.stringify(a.snapshot()), sb = JSON.stringify(b.snapshot());
console.log('seed=123 60sn iki ayri kosu bit-bit ayni:', sa === sb, '| snapshot bayt:', sa.length);

// 2) 0 girdi: seed 0, 0 tick, bos figures → crash yok
try {
  const z = engine.createEngine(0);
  z.setData({ phase: 'running', figures: [] });
  console.log('seed=0 + 0 tick + bos figures: crash yok, snapshot var:', !!z.snapshot());
} catch (e) { console.log('seed=0 HATA:', e.message); }

// 3) negatif/sifir dt: crash yok, sayilar sonlu
try {
  const n = engine.createEngine(1); twoFighters(n);
  n.tick(-1); n.tick(0); n.tick(1 / 60);
  const s = n.snapshot();
  const finite = s.fighters.every(f => Number.isFinite(f.x) && Number.isFinite(f.hp));
  console.log('negatif/sifir dt: crash yok, sayilar sonlu:', finite);
} catch (e) { console.log('neg dt HATA:', e.message); }

// 4) 60sn/3600 adim probe (seed 123): hasar barlari 0..100, pozisyonlar sonlu
let bad = 0, steps = 0;
const p = engine.createEngine(123); twoFighters(p);
for (let i = 0; i < 60 * 60; i++) {
  p.tick(1 / 60); steps++;
  const s = p.snapshot();
  for (const f of s.fighters) {
    if (!(f.hp >= 0 && f.hp <= 100) || !Number.isFinite(f.x) || !Number.isFinite(f.y || 0)) bad++;
  }
}
console.log('60sn/3600 adim probe: bozuk-deger sayimi =', bad, '(0 beklenir)');
