#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
JAR="${TLA_JAR:-$ROOT/.cache/tla2tools-v1.8.0.jar}"

if [[ ! -f "$JAR" ]]; then
  mkdir -p "$(dirname "$JAR")"
  curl --fail --location --retry 3 --silent --show-error \
    https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar \
    --output "$JAR"
fi
echo "0b57675323babad8ab3815b1db9f30e75dac1cdb  $JAR" | sha1sum --check --status

python3 "$ROOT/tools/verify_state_machines.py"
python3 "$ROOT/formal/longshot/source_contract.py" "$ROOT"
python3 "$ROOT/formal/longshot/mutate.py" "$ROOT/formal/longshot/specs" "$OUT"

for model in ShareIngress ProcessResult SliceCuts ExportSelection; do
  src="$ROOT/formal/longshot/specs"
  echo "TLC: $model"
  java -Xmx1024m -cp "$JAR" tlc2.TLC -workers 1 \
    -config "$src/$model.cfg" "$src/$model.tla" > "$OUT/$model.pass.log" 2>&1 \
    || { cat "$OUT/$model.pass.log"; exit 1; }
  grep -q "Model checking completed. No error has been found." "$OUT/$model.pass.log" \
    || { cat "$OUT/$model.pass.log"; exit 1; }
  grep -E "distinct states found|states generated" "$OUT/$model.pass.log" | tail -1 || true

  echo "MUTATION: $model must fail on a broken design"
  if java -Xmx1024m -cp "$JAR" tlc2.TLC -workers 1 \
    -config "$OUT/$model/$model.cfg" "$OUT/$model/$model.tla" \
    > "$OUT/$model.bad.log" 2>&1; then
    echo "FAIL: mutant unexpectedly passed: $model"
    cat "$OUT/$model.bad.log"
    exit 1
  fi
  if ! grep -Eiq "Invariant .* (is violated|violated)" "$OUT/$model.bad.log"; then
    echo "FAIL: mutant failed for a reason other than invariant violation: $model"
    cat "$OUT/$model.bad.log"
    exit 1
  fi
  grep -Ei "Invariant .* (is violated|violated)" "$OUT/$model.bad.log" | head -1
done

echo "PASS: four bounded TLC models, four invariant-killing mutants, and Kotlin source-contract mutation probes."
