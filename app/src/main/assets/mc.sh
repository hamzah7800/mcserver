#!/data/data/com.termux/files/usr/bin/bash
# Core script. Run by the app inside Termux: bash -c "<this>" mc <command> [args]
export PATH=/data/data/com.termux/files/usr/bin:$PATH
DIR="$HOME/mcserver"; CONF="$DIR/manager.conf"; UA="s20fe-mc-app/1.0"
BASE_MODS="fabric-api lithium ferrite-core modernfix krypton spark"
[ -f "$CONF" ] && . "$CONF"

mod_add() {
  local slug="$1" url
  url=$(curl -fsSL -A "$UA" -G "https://api.modrinth.com/v2/project/$slug/version" \
    --data-urlencode "game_versions=[\"$MC_VERSION\"]" \
    --data-urlencode 'loaders=["fabric"]' |
    jq -r '[.[]|select(.version_type=="release")][0].files // [] | map(select(.primary))[0].url // empty')
  if [ -z "$url" ]; then echo "  x $slug: no Fabric $MC_VERSION release found"; return 1; fi
  curl -fsSL -A "$UA" -o "$DIR/mods/$(basename "$url")" "$url" && echo "  ok $slug"
}

install_all() {
  echo "== Installing packages =="
  pkg update -y -o Dpkg::Options::=--force-confnew
  pkg install -y -o Dpkg::Options::=--force-confnew openjdk-21 curl jq tmux || { echo "Package install failed"; return; }
  mkdir -p "$DIR/mods" && cd "$DIR" || return
  printf 'MC_VERSION=%s\nRAM=%s\n' "$MC_VERSION" "$RAM" > "$CONF"

  echo "== Fabric server $MC_VERSION =="
  local L I
  L=$(curl -fsSL https://meta.fabricmc.net/v2/versions/loader | jq -r '[.[]|select(.stable)][0].version')
  I=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | jq -r '[.[]|select(.stable)][0].version')
  curl -fL -o fabric-server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC_VERSION/$L/$I/server/jar" \
    || { echo "Could not download Fabric for $MC_VERSION"; return; }

  echo "== Performance mods =="
  for m in $BASE_MODS; do mod_add "$m"; done

  if [ "$ACCEPT" = "yes" ]; then echo "eula=true" > eula.txt; else echo "EULA not accepted - server won't start."; fi

  cat > server.properties <<EOP
view-distance=6
simulation-distance=4
network-compression-threshold=256
max-players=10
online-mode=true
sync-chunk-writes=false
entity-broadcast-range-percentage=75
spawn-protection=0
motd=S20 FE Server
EOP

  echo "== playit.gg =="
  if ! command -v playit >/dev/null; then
    pkg install -y playit 2>/dev/null || {
      curl -fL -o "$PREFIX/bin/playit" \
        https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-aarch64 \
        && chmod +x "$PREFIX/bin/playit"
    }
  fi
  command -v playit >/dev/null && echo "playit ready" || echo "playit install FAILED"
}

start_all() {
  [ -f "$DIR/fabric-server.jar" ] || { echo "Run Install first."; return; }
  termux-wake-lock 2>/dev/null
  cd "$DIR" || return
  tmux has-session -t mc 2>/dev/null || tmux new-session -d -s mc "java -Xms$RAM -Xmx$RAM -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
    -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
    -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:SurvivorRatio=32 -XX:MaxTenuringThreshold=1 -XX:+PerfDisableSharedMem \
    -jar fabric-server.jar nogui"
  tmux has-session -t playit 2>/dev/null || tmux new-session -d -s playit "playit"
}

stop_all() {
  if tmux has-session -t mc 2>/dev/null; then
    tmux send-keys -t mc "stop" Enter
    for _ in $(seq 1 40); do tmux has-session -t mc 2>/dev/null || break; sleep 1; done
  fi
  tmux kill-session -t playit 2>/dev/null
  termux-wake-unlock 2>/dev/null
}

backup() {
  mkdir -p "$HOME/mc-backups"
  local f="$HOME/mc-backups/world-$(date +%F-%H%M).tar.gz"
  if tmux has-session -t mc 2>/dev/null; then tmux send-keys -t mc "save-off" Enter "save-all flush" Enter; sleep 5; fi
  tar -czf "$f" -C "$DIR" world 2>/dev/null && echo "Saved $f" || echo "No world yet."
  tmux has-session -t mc 2>/dev/null && tmux send-keys -t mc "save-on" Enter
}

case "$1" in
  install) MC_VERSION="${2:-1.21.1}"; RAM="${3:-3G}"; ACCEPT="$4"; install_all; read -rp "Done. Press Enter to close." _ ;;
  start)   start_all ;;
  stop)    stop_all ;;
  backup)  backup ;;
  addmod)  mod_add "$2"; read -rp "Press Enter to close." _ ;;
  console) tmux attach -t mc || { echo "Server not running"; sleep 3; } ;;
  link)    tmux attach -t playit || { echo "playit not running - start the server first"; sleep 3; } ;;
esac
