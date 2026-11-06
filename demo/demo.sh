#!/usr/bin/env bash
# Scripted demo for the screen recording (Pleos Connect Emulator v2.1.0).
#
# Real Pleos SDK values are driven through the emulator's Virtual Console HTTP API
# (gear, speed, cruise state, odometer). Only values the console cannot produce come from the
# in-app simulator (fuel %, time acceleration, route progress). The app shows SDK/SIM next to every value.
# While driving, the navigation stays in front and the app speaks + shows a heads-up card on top of the map.
#
# usage: demo.sh setup | part1 | part2        (REC=1 records the screen to demo/out/<part>.mp4
#                                               and writes caption timings to demo/out/<part>.captions.tsv)
set -u
export MSYS_NO_PATHCONV=1   # Git Bash: keep /sdcard/... as a device path
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb}"
V="http://127.0.0.1:8088"
OUT="$(dirname "$0")/out"; mkdir -p "$OUT"
CLIP=""; T0=0

d()   { if [ $# -ge 2 ]; then "$ADB" shell "am broadcast -a ai.highwaymate.DEMO --es cmd $1 --es v '$2'" >/dev/null
        else "$ADB" shell am broadcast -a ai.highwaymate.DEMO --es cmd "$1" >/dev/null; fi; }
vc()  { curl -s -X PUT "$V/vehicle/$1" -o /dev/null; }       # Virtual Console write (real SDK path)
app() { "$ADB" shell am start -n ai.highwaymate/.ui.MainActivity >/dev/null 2>&1; }
navi(){ "$ADB" shell am start -n ai.umos.maps.android.navigation.app/.MainActivity >/dev/null 2>&1 || "$ADB" shell input tap 1152 1370; }
tap() { "$ADB" shell input tap "$1" "$2"; }
odo() { curl -s "$V/vehicle/state" | python -I -c "import json,sys; print(int(json.load(sys.stdin)['odometer']['traveled_distance_km']))"; }
now() { python -I -c "import time; print(f'{time.time():.2f}')"; }
# caption: written with its start time; the video tool burns it in afterwards
cap() { [ -n "$CLIP" ] && printf '%s\t%s\n' "$(python -I -c "import time; print(f'{time.time()-$T0:.2f}')")" "$1" >> "$OUT/$CLIP.captions.tsv"; echo "  [cap] $1"; }

heartbeat() { while true; do curl -s -X POST "$V/browser/session?client=hm-demo" -o /dev/null; sleep 1; done; }

rec_start() {
  CLIP="$1"; : > "$OUT/$CLIP.captions.tsv"
  if [ "${REC:-0}" = 1 ]; then
    "$ADB" shell screenrecord --time-limit 178 --size 1920x1080 --bit-rate 12000000 "/sdcard/$1.mp4" &
    REC_PID=$!
  fi
  sleep 1; T0=$(now)
}
rec_stop() {
  cap ""
  [ "${REC:-0}" = 1 ] || return 0
  sleep 1; "$ADB" shell pkill -INT screenrecord; wait "$REC_PID" 2>/dev/null; sleep 2
  "$ADB" pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null && echo "saved $OUT/$1.mp4"
}

"$ADB" forward tcp:8088 tcp:8088 >/dev/null
heartbeat & HB=$!
trap 'kill $HB 2>/dev/null' EXIT
sleep 1.5

case "${1:-}" in
setup)
  vc "controls?speed=0&accelerator=0&brake=0"; vc "driving-mode?ad_mode_state=not_ready"; vc "gear?gear=Park"
  vc "driving-mode?target_speed_mps=27.778"
  d reset; d clearlog; d fuel 32; d panel on; d temp 22; d mode VOICE; d tab 0; app
  echo "odometer now: $(odo) km"
  ;;

part1)
  app; sleep 1; rec_start hm_part1
  cap "하이웨이 메이트 — 업무 운전자를 위한 고속도로 안전 코치 + 자동 운행일지"; sleep 5
  cap "정차 중 앱 화면 · 오른쪽 패널: SDK = 실제 Pleos SDK 값, SIM = 시뮬레이터 값"; sleep 5
  cap "목적지를 정하면 실제 NaviHelper로 순정 내비에 경로 요청 (대전 141km)"; d route; sleep 6
  cap "출발: 기어 D · 100km/h (Virtual Console → 실제 SDK). 주행 중엔 지도가 앞에, 앱은 뒤에서 동작"
  vc "gear?gear=Drive"; sleep 1; vc "controls?speed=100&accelerator=20&brake=0"; sleep 6
  cap "고속도로 주행보조(HDA)를 켜면 크루즈 「작동」 → 예고 모드"
  vc "driving-mode?ad_mode_state=full_autonomous"; sleep 3
  cap "실제 내비 정체 정보로 정체 구간 전에 미리: 주의 구간 카드 + 소리 (녹화엔 소리 없음)"; sleep 7
  cap "요금소·분기점도 미리 알려요 (실제 내비 TBT)"; sleep 7
  cap "시간 빠르게 ×60 · 연속 운전 1시간 50분 · 연료 28%"
  d fuel 28; d drive 106; d time 60; sleep 5; d time 1; sleep 3
  cap "휴게소 권유 카드 — 지도를 떠나지 않고 버튼 한 번"; sleep 4
  tap 1446 288; sleep 1
  cap "순정 내비 경로에 휴게소 경유지가 들어갔어요"; sleep 7
  cap "휴게소 도착 · P단 → 앱을 열면 오늘 운전 요약"
  d atrest; sleep 1
  vc "driving-mode?ad_mode_state=ready"; vc "controls?speed=0&accelerator=0&brake=30"; sleep 1; vc "gear?gear=Park"; sleep 2
  app; sleep 6
  cap "주유하면 연료 증가를 감지해 운행일지에 함께 기록"; d fuel 75; sleep 5
  rec_stop hm_part1
  ;;

part2)
  O=$(odo)
  app; sleep 1; rec_start hm_part2
  cap "10분 쉬면 연속 운전 시간이 초기화돼요 (시간 ×60)"; d time 60; sleep 11; d time 1; sleep 2
  cap "다시 출발 → 목적지 직전. 계기판 누적거리도 실제 SDK로 +141km"
  navi; vc "gear?gear=Drive"; sleep 1; vc "controls?speed=90&accelerator=20&brake=0"; d at 140300
  vc "odometer?traveled_distance_km=$((O + 141))"; sleep 8
  cap "도착 · P단 → 계기판 값으로 운행이 자동 기록, 운전자는 「업무」 한 번만"
  vc "controls?speed=0&accelerator=0&brake=30"; sleep 1; vc "gear?gear=Park"; sleep 1; app; sleep 5
  d classify business; sleep 4
  cap "월말: 국세청 운행기록부 서식 CSV를 QR로 — 서버를 거치지 않아요"; d tab 2; sleep 7
  cap "알림 설정: 음성 / 효과음(주의 띵, 위험 띵띵띵) / 화면만, 알림 종류별 켜기·끄기"; d tab 4; sleep 7
  cap "전기차 확장 ⑤ (2단계): 내비로 도착 예상 배터리 확인"; d tab 3; d trip; sleep 6
  cap "중간 절전 → 공조 온도·풍량·조명 (차량 제어 SDK, 아래 공조 표시 22° → 20°)"; d save mid; sleep 8
  cap "하이웨이 메이트 — 감지는 순정이, 예고와 다음 행동은 앱이"; d tab 0; sleep 5
  rec_stop hm_part2
  ;;
*) echo "usage: $0 setup|part1|part2"; exit 1 ;;
esac
