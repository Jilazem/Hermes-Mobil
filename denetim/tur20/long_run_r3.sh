# Tur-20 r3: PID-sabit 130sn kosu — her adb cagisi 20sn timeout'lu (takilma koruma).
# Bash 3.2 (macOS) — timeout yok; arka-plan + PID ile kill.
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
S=emulator-5554
PKG=com.hermes.mobile.v2
OUT=denetim/tur20/kanit/uzun-kosu
mkdir -p "$OUT"
TS() { date "+%H:%M:%S"; }
P() { echo "[$(TS)] $1" | tee -a "$OUT/kosu-serisi.txt"; }

# adb_ <timeout_sn> <args...> — timeout'lu adb; 0=stdout, fail=-1
run_adb() {
  local tmo="$1"; shift
  ( "$ADB" -s "$S" "$@" >"$OUT/.adb-out.tmp" 2>&1 ) &
  local pid=$! n=0
  while kill -0 "$pid" 2>/dev/null; do
    sleep 1; n=$((n+1))
    if [ "$n" -ge "$tmo" ]; then kill -9 "$pid" 2>/dev/null; return 1; fi
  done
  wait "$pid" 2>/dev/null
  return 0
}

P "KOSU BASLADI (r3, timeout-korumali)"
run_adb 20 shell pidof "$PKG" || { P "pidof alinamadi — adb tikendi, cikis"; exit 2; }
pid0=$(tr -d '\r\n' < "$OUT/.adb-out.tmp")
P "pid-once=$pid0"
run_adb 25 exec-out screencap -p || { cp /dev/null /dev/null; }
cp "$OUT/.adb-out.tmp" "$OUT/kare-once-full.png"
[ -s "$OUT/kare-once-full.png" ] || { P "kare-once alinamadi (adb takildi), cikis"; exit 3; }

T0=$(date +%s); PIDM=""; KILLS=""
while : ; do
  sleep 5
  NOW=$(date +%s); DT=$((NOW-T0))
  [ "$DT" -ge 130 ] && break
  if [ -z "$PIDM" ] && [ "$DT" -ge 60 ]; then
    run_adb 25 exec-out screencap -p && cp "$OUT/.adb-out.tmp" "$OUT/kare-60sn-full.png"
    run_adb 20 shell pidof "$PKG" && PIDM=$(tr -d '\r\n' < "$OUT/.adb-out.tmp")
    P "60sn arasi: pid=$PIDM kare=$(stat -f%z "$OUT/kare-60sn-full.png" 2>/dev/null)B"
  fi
  # kill taramasi (events buffer, her adim timeout'lu)
  if run_adb 20 logcat -d -b events -v epoch; then
    grep am_kill "$OUT/.adb-out.tmp" | grep "$PKG" | awk -v t0="$T0" '{if ($1+0 > t0-1) print $1}' >> "$OUT/.kills.tmp" 2>/dev/null || true
  fi
done
# kosu-ici kill sayisi
KILLED=0
if [ -f "$OUT/.kills.tmp" ]; then
  KILLED=$(sort -u "$OUT/.kills.tmp" | wc -l | tr -d ' ')
  # sadece BU kosu baslangicindan sonraki satirlar sayildi (awk filtresi)
fi
run_adb 20 shell pidof "$PKG"; pid1=$(tr -d '\r\n' < "$OUT/.adb-out.tmp")
run_adb 25 exec-out screencap -p && cp "$OUT/.adb-out.tmp" "$OUT/kare-son-full.png"
run_adb 30 logcat -d -v time; cp "$OUT/.adb-out.tmp" "$OUT/logcat-dump.txt"
P "KOSU TAMAM 130sn: pid-once=$pid0 60sn=$PIDM son=$pid1 kosu-ici-kill=$KILLED"
rm -f "$OUT/.kills.tmp"
if [ -n "$pid0" ] && [ "$pid0" = "$PIDM" ] && [ "$pid0" = "$pid1" ]; then
  P "PID-SABIT DOGRULANDI"
else
  P "PID KAYDI TUTMADI"
fi
