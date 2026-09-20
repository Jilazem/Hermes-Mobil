#!/usr/bin/env node
/*
 * Tur-20: kafes dövüşü MOTORU birim testleri (node, bağımlılık yok).
 * Çalıştırma: node tools/tur20/engine_test.js   → çıkış 0 = geçti (fail=0).
 *
 * Kapsam: deterministik seed, giriş animasyonu, saldırı→vuruş zinciri,
 * kombo sayacı, hasar barı, sendeleme (whiff/stagger), zafer + konfeti,
 * durdur → nefes, 5 sn kuralı (state değişimi), 3+ dövüşçü → tribün.
 */
'use strict';
const path = require('path');
const engine = require(path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'arena', 'cage-engine.js'));

let passed = 0, failed = 0;
function ok(cond, name) {
  if (cond) { passed++; console.log('  PASS ' + name); }
  else { failed++; console.log('  FAIL ' + name); }
}
function section(t) { console.log('— ' + t); }

function twoFighters(eng, sA, sB) {
  eng.setData({
    phase: 'running',
    figures: [
      { id: 'a#r1', name: 'coder', state: sA || 'working', badge: 'Tur 1' },
      { id: 'b#r1', name: 'android', state: sB || 'working', badge: 'Tur 1' },
    ],
  });
}
function run(eng, secs, dt) {
  dt = dt || 1 / 60;
  for (let i = 0; i < Math.round(secs / dt); i++) eng.tick(dt);
}

section('1. deterministik seed — aynı veri aynı sahne');
{
  const e1 = engine.createEngine(42), e2 = engine.createEngine(42);
  twoFighters(e1); twoFighters(e2);
  run(e1, 20); run(e2, 20);
  const s1 = JSON.stringify(e1.snapshot()), s2 = JSON.stringify(e2.snapshot());
  ok(s1 === s2, 'iki ayrı motor, aynı seed → aynı snapshot (20 sn simülasyon)');
  const e3 = engine.createEngine(43);
  twoFighters(e3); run(e3, 20);
  ok(JSON.stringify(e3.snapshot()) !== s1, 'farklı seed → farklı sahne');
}

section('2. giriş animasyonu — dövüşçüler ringe yürür');
{
  const e = engine.createEngine(7);
  twoFighters(e);
  let s = e.snapshot();
  ok(s.fighters[0].pose === 'enter' && s.fighters[0].x < -4, 'başta ring dışında, pose=enter');
  run(e, 0.5);
  s = e.snapshot();
  ok(s.fighters[0].x > -6.5 + 0.5, 'giriş sırasında x ilerliyor (hareket var)');
  run(e, 2.5);
  s = e.snapshot();
  ok(s.fighters.every(f => f.pose !== 'enter'), 'giriş süresi bitince pose=enter kalmaz');
  const evs = e.drainEvents();
  ok(evs.some(ev => ev.type === 'entered'), "'entered' olayı yayınlandı");
}

section('3. saldırı → vuruş zinciri, kombo ve hasar barı');
{
  const e = engine.createEngine(11);
  twoFighters(e);
  run(e, 25);
  const evs = e.drainEvents();
  const hits = evs.filter(ev => ev.type === 'hit');
  ok(hits.length > 0, '25 sn içinde en az bir vuruş oldu (' + hits.length + ')');
  const s = e.snapshot();
  ok(s.fighters.some(f => f.hp < 100), 'hasar barı düştü (hp < 100)');
  const withCombo = hits.some(h => h.combo >= 1);
  ok(withCombo, 'vuruş olayı kombo sayacı taşıyor');
  ok(hits.every(h => h.dmg > 0 && h.dmg < 20 && Number.isInteger(h.dmg)), 'hasar 1..19 tam sayı');
}

section('4. 5 saniye kuralı — 5 sn penceresinde state/eylem değişimi');
{
  const e = engine.createEngine(5);
  twoFighters(e);
  run(e, 1.6); // giriş bitsin
  let last = JSON.stringify(e.snapshot().fighters.map(f => f.pose + '|' + f.x.toFixed(3) + '|' + f.combo));
  let maxStaticWindow = 0, windowStart = 0;
  const dt = 1 / 60;
  for (let i = 0; i < 60 * 30; i++) {
    e.tick(dt);
    const cur = JSON.stringify(e.snapshot().fighters.map(f => f.pose + '|' + f.x.toFixed(3) + '|' + f.combo));
    const t = 1.6 + i * dt;
    if (cur !== last) { maxStaticWindow = Math.max(maxStaticWindow, t - windowStart); windowStart = t; last = cur; }
  }
  ok(maxStaticWindow < 5, 'en uzun sabit pencere < 5 sn (' + maxStaticWindow.toFixed(2) + ' sn) — hareket sürüyor');
}

section('5. zafer + konfeti (görev tamamlanınca)');
{
  const e = engine.createEngine(9);
  twoFighters(e, 'working', 'working');
  run(e, 3);
  e.setData({
    phase: 'done',
    figures: [
      { id: 'a#r1', name: 'coder', state: 'done', badge: 'Tur 1' },
      { id: 'b#r1', name: 'android', state: 'waiting', badge: 'Tur 1' },
    ],
  });
  run(e, 1.2);
  const s = e.snapshot();
  const a = s.fighters.find(f => f.id === 'a#r1'), b = s.fighters.find(f => f.id === 'b#r1');
  ok(s.winnerId === 'a#r1', 'kazanan işaretlendi');
  ok(a.pose === 'victory', 'kazanan zafer pozunda');
  ok(b.pose === 'down', 'kaybeden yığıldı');
  ok(s.confettiT >= 0, 'konfeti zamanı kuruldu');
  const evs = e.drainEvents();
  ok(evs.some(ev => ev.type === 'victory') && evs.some(ev => ev.type === 'confetti'), 'victory + confetti olayları');
}

section('6. durdur → sakince nefes');
{
  const e = engine.createEngine(3);
  twoFighters(e);
  run(e, 5);
  e.setData({
    phase: 'stopped',
    figures: [
      { id: 'a#r1', name: 'coder', state: 'waiting' },
      { id: 'b#r1', name: 'android', state: 'waiting' },
    ],
  });
  run(e, 2.2);
  const s = e.snapshot();
  ok(s.winnerId === null, 'durdurmadan sonra kazanan yok');
  ok(s.fighters.every(f => f.pose === 'idle' || f.pose === 'enter'), 'dövüşçüler idle/a girişte — kavga yok');
}

section('7. 3+ dövüşcü → tribün (uydurma dövüşcü YOK)');
{
  const e = engine.createEngine(21);
  e.setData({
    phase: 'running',
    figures: [
      { id: 'a#r1', name: 'coder', state: 'working' },
      { id: 'b#r1', name: 'android', state: 'working' },
      { id: 'c#r1', name: 'yazar', state: 'waiting' },
      { id: 'd#r1', name: 'tasarim', state: 'waiting' },
    ],
  });
  const s = e.snapshot();
  ok(s.fighters.length === 2, 'ringde tam 2 dövüşcü');
  ok(s.spectators.length === 2, 'kalan 2 figür tribünde');
}

section('8. hata (kritik başarısızlık) → sendeleme + hasar');
{
  const e = engine.createEngine(13);
  twoFighters(e, 'working', 'working');
  run(e, 2);
  e.setData({
    phase: 'running',
    figures: [
      { id: 'a#r1', name: 'coder', state: 'working' },
      { id: 'b#r1', name: 'android', state: 'error' },
    ],
  });
  const evs = e.drainEvents();
  ok(evs.some(ev => ev.type === 'stagger' && ev.id === 'b#r1'), "state='error' → stagger olayı");
  const s = e.snapshot();
  ok(s.fighters.find(f => f.id === 'b#r1').hp < 100, 'hatada can düştü');
}

section('9. boş/hatalı veriye dayanıklılık');
{
  const e = engine.createEngine(1);
  e.setData(null); e.setData({}); e.setData({ phase: 'running', figures: [] });
  run(e, 1);
  ok(e.snapshot().fighters.length === 0, 'bozuk veri motoru düşürmedi');
  e.tick(NaN); // çılgın dt
  ok(Number.isFinite(e.snapshot().t), 'NaN dt zamanı bozmadı (clamp devrede)');
}

section('10. hasar barı 0 altına inmez, boşluk YOK');
{
  const e = engine.createEngine(77);
  twoFighters(e);
  run(e, 60);
  const s = e.snapshot();
  ok(s.fighters.every(f => f.hp >= 0 && f.hp <= 100), 'hp 0..100 aralığında (60 sn simülasyon)');
  ok(Number.isFinite(s.fighters[0].x) && Number.isFinite(s.fighters[1].x), 'pozisyonlar sonlu');
}

console.log('\nSONUC: tests=' + (passed + failed) + ' passed=' + passed + ' failed=' + failed);
process.exit(failed ? 1 : 0);
