# v1.6.1 性能验证

## 测量边界

- `tools/performance_query_benchmark.py` 是主机端 SQLite 查询形状基准，不是 Android 启动、帧或 Room 设备基准。
- 每项执行 25 次，表格为中位数；数据集固定为每条 session 含 3 条 readings、长备注和症状。
- Android 设备指标由 `baselineprofile` 模块采集。当前本机没有连接设备，因此不填写启动毫秒数或帧数据。

## 主机端 SQLite 结果

| 记录数 | 场景 | 修改前中位数 | v1.6.1 中位数 | 返回/加载行数（前→后） |
|---:|---|---:|---:|---:|
| 0 | 月历 | 0.0033 ms | 0.0013 ms | 0 → 0 |
| 0 | 单日 | 0.0032 ms | 0.0020 ms | 0 → 0 |
| 100 | 月历 | 0.3598 ms | 0.0126 ms | 400 → 31 |
| 100 | 单日 | 0.3728 ms | 0.0040 ms | 400 → 1 |
| 1,000 | 月历 | 3.9508 ms | 0.0281 ms | 4,000 → 62 |
| 1,000 | 单日 | 3.9392 ms | 0.0060 ms | 4,000 → 2 |
| 10,000 | 月历 | 44.4335 ms | 0.2067 ms | 40,000 → 434 |
| 10,000 | 单日 | 43.9057 ms | 0.0273 ms | 40,000 → 14 |
| 10,000 | 首页 | 43.5372 ms | 1.1449 ms | 40,000 → 2 |
| 10,000 | 趋势摘要 | 7.6277 ms | 4.0779 ms | 10,000 → 1 |

0 条时首页和趋势聚合仍返回一行 SQL 聚合结果，因此微秒级结果略慢；这避免了额外状态分支，且不会随历史规模增长。

## 可重复命令

```powershell
python tools/performance_query_benchmark.py --repeats 25
./gradlew :baselineprofile:assembleBenchmarkRelease
./gradlew :app:generateBaselineProfile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

设备基准需要 API 33+ 模拟器或真机。执行后应保存 Macrobenchmark JSON 与 Perfetto trace，再比较 `CompilationMode.None` 和 Baseline Profile 的冷启动结果。
