#!/usr/bin/env bash
# Usage: ./bench.sh <filename-inside-dir> [dir=files] [port=5000]
# Runs trad and nio, each with 1 and 10 workers, 3 runs each -> results.csv
FILE=${1:?usage: ./bench.sh <filename> [dir] [port]}
DIR=${2:-files}
PORT=${3:-5000}
RUNS=3
OUT=/tmp/dl.bin

if command -v sha256sum >/dev/null; then HASHCMD="sha256sum"; else HASHCMD="shasum -a 256"; fi
REF=$($HASHCMD "$DIR/$FILE" | cut -d' ' -f1)

echo "mode,workers,run,time_ms,mbps,size_ok,hash_ok" > results.csv

for MODE in trad nio; do
  java -cp out Server "$PORT" "$DIR" "$MODE" > /dev/null &
  SPID=$!
  sleep 1
  for W in 1 10; do
    for R in $(seq 1 $RUNS); do
      rm -f "$OUT"
      LINE=$(java -cp out Client localhost "$PORT" "$FILE" "$MODE" "$W" "$OUT" | grep '^RESULT')
      T=$(echo "$LINE"  | sed -n 's/.*time_ms=\([0-9]*\).*/\1/p')
      M=$(echo "$LINE"  | sed -n 's/.*mbps=\([0-9.]*\).*/\1/p')
      S=$(echo "$LINE"  | sed -n 's/.*size_ok=\([a-z]*\).*/\1/p')
      H=$(echo "$LINE"  | sed -n 's/.*sha256=\([0-9a-f]*\).*/\1/p')
      OK=false; [ "$H" = "$REF" ] && OK=true
      echo "$MODE,$W,$R,$T,$M,$S,$OK" | tee -a results.csv
    done
  done
  kill "$SPID"; wait "$SPID" 2>/dev/null
done
echo "Done -> results.csv"
