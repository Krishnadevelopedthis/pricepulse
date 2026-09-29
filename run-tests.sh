#!/usr/bin/env bash
# Runs everything that can run without Maven/MongoDB/PostgreSQL/Chrome. Requires JDK 21 and Node 20+.
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
echo "== Backend core logic (plain Java) =="
javac -d "$OUT" $(find backend/src/main/java/com/pricepulse/util backend/src/test -name '*.java')
java -cp "$OUT" com.pricepulse.util.CoreLogicSelfTest | tail -3
echo "== Academic Java demos =="
javac -d "$OUT" -sourcepath backend/src/main/java academic/networking/*.java academic/concurrency/*.java
for c in TcpDemo UdpDemo UrlDemo InetDiagnostics ThreadLifecycleDemo SynchronizationDemo WaitNotifyDemo ExecutorDemo; do
  java -cp "$OUT" "$c" >/dev/null && echo "PASS $c"
done
javac -d "$OUT" $(find academic/jdbc -name '*.java') && echo "PASS jdbc module compiles (running it needs PostgreSQL)"
echo "== Extension logic (Node) =="
(cd tests && [ -d node_modules ] || npm install --silent; node --test 2>&1 | grep -E '^# (tests|pass|fail)')
