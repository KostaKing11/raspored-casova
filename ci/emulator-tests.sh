#!/usr/bin/env bash
# Runs on the CI Android emulator: instrumentation tests, screenshots, notification delivery
# in Doze, alarm re-scheduling after reboot / time change / app update.
set -uo pipefail

PKG=com.kostaking.raspored.debug
TEST_PKG=com.kostaking.raspored.debug.test
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
OUT=ci-out
mkdir -p "$OUT/screenshots"
RESULTS="$OUT/results.md"
: > "$RESULTS"
FAILED=0

pass() { echo "✅ $*" | tee -a "$RESULTS"; }
fail() { echo "❌ $*" | tee -a "$RESULTS"; FAILED=1; }
info() { echo "ℹ️ $*" | tee -a "$RESULTS"; }

wait_boot() {
  adb wait-for-device
  for i in $(seq 1 120); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return 0
    sleep 2
  done
  return 1
}

bcast() { adb shell am broadcast -a "com.kostaking.raspored.debug.$1" -p $PKG "${@:2}" >/dev/null; }

set_time() { # $1 = MMDDhhmmYYYY.ss  (local time, Europe/Belgrade)
  adb shell "date $1" >/dev/null
}

adb root >/dev/null 2>&1 || true
sleep 3
wait_boot
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
info "Emulator: Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), TZ $(adb shell getprop persist.sys.timezone | tr -d '\r')"

# ---------------------------------------------------------------- 0. Release (R8) smoke test
if [ -f release-smoke.apk ]; then
  adb logcat -c
  adb install -r -g release-smoke.apk >/dev/null
  adb shell am start -W -n com.kostaking.raspored/.MainActivity >/dev/null
  sleep 8
  adb exec-out screencap -p > "$OUT/screenshots/00_release_onboarding.png"
  if adb shell pidof com.kostaking.raspored >/dev/null && ! adb logcat -d | grep -q "FATAL EXCEPTION"; then
    pass "Release APK (R8/minify) se pokreće bez pada"
  else
    fail "Release APK pada pri pokretanju"; adb logcat -d | grep -A 20 "FATAL EXCEPTION" | head -n 40
  fi
  adb uninstall com.kostaking.raspored >/dev/null
fi

# ---------------------------------------------------------------- 1. Instrumentation tests
# Thursday 15 Oct 2026, 10:40 -> during the Fizika block (nice screenshots with "now" line)
set_time 101510402026.00
adb install -r -g "$APK" >/dev/null && adb install -r -g "$TEST_APK" >/dev/null
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am instrument -w -r $TEST_PKG/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrumentation.txt" 2>&1
grep -E "INSTRUMENTATION_STATUS: (test|stack)=|INSTRUMENTATION_STATUS_CODE|^OK|FAILURES|Tests run" "$OUT/instrumentation.txt" | tail -n 80
if grep -q "^OK (" "$OUT/instrumentation.txt"; then
  pass "Instrumentation testovi: $(grep '^OK (' "$OUT/instrumentation.txt")"
else
  fail "Instrumentation testovi nisu prošli (vidi instrumentation.txt)"
  grep -A 30 "INSTRUMENTATION_STATUS: stack=" "$OUT/instrumentation.txt" | head -n 120
fi
adb logcat -d -s RasporedTest:I | tail -n 5 | tee -a "$RESULTS"

for f in $(adb shell run-as $PKG ls files/screenshots 2>/dev/null | tr -d '\r'); do
  adb exec-out run-as $PKG cat "files/screenshots/$f" > "$OUT/screenshots/$f"
done
info "Screenshotova: $(ls "$OUT/screenshots" | wc -l)"

# ---------------------------------------------------------------- 2. Real lesson notification in Doze
# Thursday 11:47:00 -> next planned alarm is 11:50:00 (end of Fizika 3–4, next: Srpski 301)
bcast SET_MODE --es mode ALARM_CLOCK
set_time 101511472026.00
bcast RESCHEDULE
sleep 3
adb shell input keyevent KEYCODE_HOME
adb logcat -c
adb shell dumpsys battery unplug
adb shell input keyevent 223   # screen off
sleep 2
adb shell dumpsys deviceidle force-idle
STATE=$(adb shell dumpsys deviceidle get deep | tr -d '\r')
info "Doze (deep) stanje pre čekanja: $STATE"
adb shell dumpsys alarm | grep -A2 "$PKG" | head -n 12 > "$OUT/dumpsys-alarm-doze.txt" || true
sleep 200   # until ~11:50:20
LINE=$(adb logcat -d -s RasporedAlarm:I | grep "delivered kind=NEXT_LESSON" | head -n 1)
echo "$LINE"
if [ -n "$LINE" ]; then
  DELAY=$(echo "$LINE" | sed -E 's/.*delayMs=(-?[0-9]+).*/\1/')
  TITLE=$(echo "$LINE" | sed -E 's/.*title="([^"]*)".*/\1/')
  if [ "$DELAY" -lt 3000 ]; then pass "Doze + setAlarmClock: \"$TITLE\" isporučena u 11:50:00, kašnjenje ${DELAY} ms"
  else fail "Doze + setAlarmClock: kašnjenje ${DELAY} ms"; fi
else
  fail "Doze + setAlarmClock: notifikacija 'Sledeći čas' nije isporučena"
fi
adb shell dumpsys notification --noredact 2>/dev/null | grep -E "android.title=.*Sledeći čas" | head -n 2 | tee -a "$RESULTS" || true
adb shell dumpsys deviceidle unforce >/dev/null

# ---------------------------------------------------------------- 3. setExactAndAllowWhileIdle in Doze
bcast SET_MODE --es mode EXACT_IDLE
sleep 2
adb logcat -c
adb shell dumpsys deviceidle force-idle
bcast TEST_ALARM --el seconds 60
sleep 80
LINE=$(adb logcat -d -s RasporedAlarm:I | grep "delivered kind=TEST" | head -n 1)
if [ -n "$LINE" ]; then
  DELAY=$(echo "$LINE" | sed -E 's/.*delayMs=(-?[0-9]+).*/\1/')
  if [ "$DELAY" -lt 3000 ]; then pass "Doze + setExactAndAllowWhileIdle: test notifikacija, kašnjenje ${DELAY} ms"
  else fail "Doze + setExactAndAllowWhileIdle: kašnjenje ${DELAY} ms"; fi
else
  fail "Doze + setExactAndAllowWhileIdle: test notifikacija nije stigla"
fi
adb shell dumpsys deviceidle unforce >/dev/null
adb shell dumpsys battery reset
bcast SET_MODE --es mode ALARM_CLOCK
sleep 2

# ---------------------------------------------------------------- 4. Time change -> reschedule
adb logcat -c
set_time 101512302026.00
sleep 8
if adb logcat -d -s RasporedAlarm:I | grep -q "reason=android.intent.action.TIME_SET"; then
  pass "Promena vremena (TIME_SET) → alarmi ponovo zakazani"
else
  fail "Promena vremena: nema ponovnog zakazivanja"
fi

# ---------------------------------------------------------------- 5. App update -> reschedule
adb logcat -c
adb install -r "$APK" >/dev/null
sleep 10
if adb logcat -d -s RasporedAlarm:I | grep -q "reason=android.intent.action.MY_PACKAGE_REPLACED"; then
  pass "Update aplikacije (MY_PACKAGE_REPLACED) → alarmi ponovo zakazani"
else
  fail "Update aplikacije: nema ponovnog zakazivanja"
fi

# ---------------------------------------------------------------- 6. Reboot -> reschedule
adb reboot
sleep 15
wait_boot || fail "Emulator se nije podigao posle restarta"
adb root >/dev/null 2>&1 || true
sleep 3
wait_boot
for i in $(seq 1 30); do
  adb logcat -d -s RasporedAlarm:I | grep -q "reason=android.intent.action.BOOT_COMPLETED" && break
  sleep 3
done
if adb logcat -d -s RasporedAlarm:I | grep -q "reason=android.intent.action.BOOT_COMPLETED"; then
  N=$(adb logcat -d -s RasporedAlarm:I | grep "reason=android.intent.action.BOOT_COMPLETED" | sed -E 's/.*rescheduled ([0-9]+) alarms.*/\1/' | head -n 1)
  pass "Restart telefona (BOOT_COMPLETED) → ponovo zakazano $N alarma"
else
  fail "Restart: alarmi nisu ponovo zakazani"
fi
COUNT=$(adb shell dumpsys alarm | grep -c "$PKG" || true)
info "dumpsys alarm posle restarta: $COUNT zapisa za $PKG"
[ "$COUNT" -gt 0 ] && pass "AlarmManager posle restarta ima alarme aplikacije" || fail "AlarmManager posle restarta nema alarme aplikacije"

adb logcat -d > "$OUT/logcat-end.txt" || true
echo "------------------------------------------------"
cat "$RESULTS"
exit $FAILED
