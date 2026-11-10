#!/usr/bin/env python3
"""Fail-closed registry checks for stateful longshot subsystems.

This is a source correspondence and discovery gate, not proof of behavior.
Run independently: python3 tools/verify_state_machines.py
"""
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROOT / "docs/state-machines/registry.json"

def verify_initial_process_admission(code: str) -> bool:
    start = code.split("fun startProcess() {", 1)
    if len(start) != 2:
        return False
    block = start[1].split("private fun processInternal(", 1)[0]
    guard = block.find("if (_editor.value?.isProcessing == true) return")
    claim = block.find("_editor.value = EditorState(")
    launch = block.find("viewModelScope.launch {")
    return (
        0 <= guard < claim < launch
        and "isProcessing = true" in block[claim:launch]
        and "val generation = sourceGeneration" in block[:claim]
    )

def verify_import_claims_loading_before_launch(code: str) -> bool:
    start = code.split("fun openUri(uri: Uri, source: UriAccessSource, flags: Int = 0) {", 1)
    if len(start) != 2:
        return False
    block = start[1].split("private fun tryPersistRead(", 1)[0]
    loading = block.find('_homeState.value = HomeUiState.Loading("正在分析图片…")')
    launch = block.find("viewModelScope.launch {")
    return (
        0 <= loading < launch
        and block.find("_source.value = null") < launch
        and block.find("_editor.value = null") < launch
    )

def main() -> None:
    data = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    machines = data["machines"]
    ids = [m["id"] for m in machines]
    assert len(ids) == len(set(ids)), "Duplicate state-machine id"
    assert len(ids) >= 20, "Unexpectedly small inventory"
    allowed = {"P0", "P1", "P2", "covered"}
    seen: set[str] = set()
    for machine in machines:
        assert machine["priority"] in allowed, machine["id"]
        assert machine["required_invariant"].strip(), machine["id"]
        assert machine["states"] and machine["events"], machine["id"]
        assert machine["verification"] and machine["decision"], machine["id"]
        for source in machine["files"]:
            target = ROOT / source
            assert target.is_file(), f"{machine['id']} missing source {source}"
            seen.add(source)
            contents = target.read_text(encoding="utf-8")
            for anchor in machine["anchors"].get(source, []):
                assert anchor in contents, f"{machine['id']} stale anchor in {source}: {anchor}"
        assert set(machine["anchors"]) <= set(machine["files"]), machine["id"]

    # A new state-bearing file must be deliberately registered. This cannot
    # detect every latent state change within an already registered file.
    signals = re.compile(
        r"MutableStateFlow|rememberSaveable|remember\s*\{\s*mutableStateOf|"
        r"\bJob\?|getSharedPreferences\(|MediaColumns.IS_PENDING|"
        r"\.part[\"']|NavHost\(|onNewIntent\("
    )
    discovered = []
    for source in (ROOT / "app/src/main/java").rglob("*.kt"):
        text = source.read_text(encoding="utf-8")
        if signals.search(text):
            rel = source.relative_to(ROOT).as_posix()
            discovered.append(rel)
            assert rel in seen, f"Undocumented stateful file: {rel}"

    vm = (ROOT / "app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt").read_text()
    assert verify_initial_process_admission(vm), "Initial processing is not synchronously claimed before coroutine launch."
    assert not verify_initial_process_admission(
        vm.replace("isProcessing = true,\n            statusMessage = \"正在切片…\"", 
                   "isProcessing = false,\n            statusMessage = \"正在切片…\"")
    ), "Admission negative control did not detect broken processing claim"

    assert verify_import_claims_loading_before_launch(vm), (
        "New import must claim loading before navigation can evaluate restored route."
    )
    assert not verify_import_claims_loading_before_launch(
        vm.replace('_homeState.value = HomeUiState.Loading("正在分析图片…")',
                   "_homeState.value = HomeUiState.Idle", 1)
    ), "Import-state negative control unexpectedly passed"

    activity = (ROOT / "app/src/main/java/com/changjietu/app/MainActivity.kt").read_text()
    assert "NavigationRecovery.forProcess(" in activity
    assert "NavigationRecovery.forPreview(" in activity

    count = Counter(m["priority"] for m in machines)
    print(f"PASS: {len(machines)} state machines, {len(seen)} mapped files, {len(discovered)} discovered state-bearing Kotlin files")
    print(f"Priority breakdown: {dict(count)}")
    print("PASS: synchronous process claim / double-tap source-order contract and failing mutant.")
    print("LIMIT: source coverage is heuristic; model and device testing remain separate.")

if __name__ == "__main__":
    main()
