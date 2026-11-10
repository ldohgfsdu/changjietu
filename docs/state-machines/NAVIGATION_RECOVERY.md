# Restored back stack without in-memory source data

## Incident evidence

On 2026-11-10 the device showed the restored **切片结果** route with "尚未生成切片，请返回处理页", and the **裁切图片** route with "未选择图片，请返回首页". Both screens remained navigable although required data was absent.

**Known code facts:** Navigation Compose's `rememberNavController()` can restore its saved back stack; `LongshotViewModel` keeps `_source` and `_editor` only in-memory, initialized to null. `RecentStore` preserves metadata (URI, dimensions, count, timestamp), **not** a complete editable cutting session. No stateful screen recovered automatically when its data was missing.

**Likely trigger, not directly observed:** Android re-created the process/activity and restored a route while constructing a fresh ViewModel. Logcat/process-death instrumentation was not supplied; an explicit source clear or stale callback could also produce absent data. Do not describe the exact trigger as proven from screenshots alone.

## State-machine policy (NAV-01)

| Restored route | Required current data | Destination |
|---|---|---|
| `process` | New URI analysis in progress | Stay and show loading |
| `process` | Valid decoded source | Stay |
| `process` | No source and no loading | Return to home |
| `preview` | Rendered slices present | Stay |
| `preview` | No slices, source still loading | Stay until resolution |
| `preview` | No slices, source ready | Return to process |
| `preview` | No slices, no source or loading | Return to home |

`NavigationRecovery` is a pure Kotlin decision model. `MainActivity` executes the destination change only in `LaunchedEffect`, with `popUpTo("home")` and `launchSingleTop`, preventing invalid routes from stacking indefinitely. `openUri` sets loading synchronously before navigation, preventing a legitimate new import from being mistaken for a restored invalid route.

## Verification

- `NavigationRecoveryTest`: JVM tests cover process death/new ViewModel shape, normal source and result screens, in-flight URI import and missing result with source still available.
- `tools/verify_state_machines.py`: checks loading/source reset occurs before launch and route recovery is connected; an intentional broken loading claim must fail the guard.
- `formal/longshot/verify.sh`: existing bounded models still run. These original models **do not** prove Android back-stack restoration.
- GitHub Android verify + lint gates must pass on exact PR HEAD.

### On-device acceptance still necessary

1. Start app, import long screenshot and reach `process`; move app to background and kill **background process** (e.g. `adb shell am kill com.changjietu.app`, not `am force-stop`); reopen from Recents. Should land on a usable home rather than stranded process.
2. Reach generated `preview`, background/kill/reopen; should land on a usable home, without cycling between two dead-end pages.
3. Normal import: tap choose image; process must stay in loading until valid source is ready, not flicker back home.
4. Rotate on ready process and preview; ViewModel survives regular configuration change and must stay in the current screen with data.
5. Restore a preview route while a source exists but slice results do not; should go to process.
6. External share cold/warm start and fresh received URI must still deliver exactly once.

**Not addressed:** restoring previously generated slice files, manually edited cuts, pending export operations or temporary URI grants across process death. Recovery here means returning the user to a valid screen, *not* restoring the lost work. A full resumable session is a separate larger feature and needs durable data/permissions/file-lifetime design before implementation.
