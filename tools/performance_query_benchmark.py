"""Repeatable host-side SQLite query-shape benchmark for v1.6.1.

This is deliberately not presented as an Android startup or frame benchmark. It
compares the old full-history query shape with the bounded projections used by
the calendar, day list, dashboard, and trend summary.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import statistics
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta, timezone
from typing import Callable


DAY_MILLIS = 86_400_000
BASE_MILLIS = int(datetime(2024, 1, 1, tzinfo=timezone.utc).timestamp() * 1000)


@dataclass
class Measurement:
    size: int
    scenario: str
    old_median_ms: float
    new_median_ms: float
    speedup: float | None
    old_rows: int
    new_rows: int


def create_database(size: int) -> sqlite3.Connection:
    db = sqlite3.connect(":memory:")
    db.executescript(
        """
        PRAGMA foreign_keys = ON;
        CREATE TABLE measurement_sessions (
            id TEXT PRIMARY KEY NOT NULL,
            measuredAt INTEGER NOT NULL,
            scene TEXT NOT NULL,
            note TEXT,
            symptomsJson TEXT,
            avgSystolic INTEGER NOT NULL,
            avgDiastolic INTEGER NOT NULL,
            avgPulse INTEGER,
            category TEXT NOT NULL,
            highRiskAlertTriggered INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE INDEX index_measurement_sessions_measuredAt
            ON measurement_sessions(measuredAt);
        CREATE TABLE measurement_readings (
            id TEXT PRIMARY KEY NOT NULL,
            sessionId TEXT NOT NULL,
            orderIndex INTEGER NOT NULL,
            systolic INTEGER NOT NULL,
            diastolic INTEGER NOT NULL,
            pulse INTEGER,
            FOREIGN KEY(sessionId) REFERENCES measurement_sessions(id) ON DELETE CASCADE
        );
        CREATE UNIQUE INDEX index_measurement_readings_sessionId_orderIndex
            ON measurement_readings(sessionId, orderIndex);
        """
    )
    sessions = []
    readings = []
    for index in range(size):
        # Spread records over two years while leaving multiple records per day.
        measured_at = BASE_MILLIS + (index % 730) * DAY_MILLIS + (index % 24) * 3_600_000
        session_id = f"session-{index:05d}"
        systolic = 110 + index % 80
        diastolic = 65 + index % 50
        pulse = 55 + index % 55
        sessions.append(
            (
                session_id,
                measured_at,
                "居家安静",
                "n" * 200,
                '["头晕","心悸"]',
                systolic,
                diastolic,
                pulse,
                "NORMAL" if systolic < 130 else "STAGE1",
                int(systolic > 180 or diastolic > 120),
                measured_at,
                measured_at,
            )
        )
        for order in range(1, 4):
            readings.append(
                (
                    f"reading-{index:05d}-{order}",
                    session_id,
                    order,
                    systolic + order - 2,
                    diastolic + order - 2,
                    pulse,
                )
            )
    db.executemany(
        "INSERT INTO measurement_sessions VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        sessions,
    )
    db.executemany(
        "INSERT INTO measurement_readings VALUES (?,?,?,?,?,?)",
        readings,
    )
    db.commit()
    return db


def timed(operation: Callable[[], int], repeats: int) -> tuple[float, int]:
    samples = []
    rows = operation()
    for _ in range(repeats):
        started = time.perf_counter_ns()
        rows = operation()
        samples.append((time.perf_counter_ns() - started) / 1_000_000)
    return statistics.median(samples), rows


def benchmark_size(size: int, repeats: int) -> list[Measurement]:
    db = create_database(size)
    month_start = BASE_MILLIS + 30 * DAY_MILLIS
    month_end = month_start + 31 * DAY_MILLIS
    day_start = BASE_MILLIS + 40 * DAY_MILLIS
    day_end = day_start + DAY_MILLIS
    today_start = BASE_MILLIS + 40 * DAY_MILLIS
    today_end = today_start + DAY_MILLIS
    trend_end = BASE_MILLIS + 730 * DAY_MILLIS

    def full_history() -> tuple[list[tuple], list[tuple]]:
        sessions = db.execute(
            "SELECT * FROM measurement_sessions ORDER BY measuredAt DESC"
        ).fetchall()
        readings = db.execute(
            "SELECT * FROM measurement_readings ORDER BY sessionId, orderIndex"
        ).fetchall()
        return sessions, readings

    def old_month() -> int:
        sessions, readings = full_history()
        _ = [row for row in sessions if month_start <= row[1] < month_end]
        return len(sessions) + len(readings)

    def new_month() -> int:
        return len(
            db.execute(
                """
                SELECT measuredAt, highRiskAlertTriggered
                FROM measurement_sessions
                WHERE measuredAt >= ? AND measuredAt < ?
                ORDER BY measuredAt ASC
                """,
                (month_start, month_end),
            ).fetchall()
        )

    def old_day() -> int:
        sessions, readings = full_history()
        _ = [row for row in sessions if day_start <= row[1] < day_end]
        return len(sessions) + len(readings)

    def new_day() -> int:
        return len(
            db.execute(
                """
                SELECT id, measuredAt, avgSystolic, avgDiastolic, avgPulse,
                       category, scene, SUBSTR(note, 1, 40), highRiskAlertTriggered
                FROM measurement_sessions
                WHERE measuredAt >= ? AND measuredAt < ?
                ORDER BY measuredAt ASC, id ASC
                """,
                (day_start, day_end),
            ).fetchall()
        )

    def old_dashboard() -> int:
        sessions, readings = full_history()
        _ = sessions[0] if sessions else None
        _ = [row for row in sessions if today_start <= row[1] < today_end]
        return len(sessions) + len(readings)

    def new_dashboard() -> int:
        latest = db.execute(
            """
            SELECT id, measuredAt, avgSystolic, avgDiastolic,
                   category, highRiskAlertTriggered
            FROM measurement_sessions
            ORDER BY measuredAt DESC, id ASC LIMIT 1
            """
        ).fetchall()
        stats = db.execute(
            """
            SELECT COUNT(*), AVG(avgSystolic), AVG(avgDiastolic)
            FROM measurement_sessions
            WHERE measuredAt >= ? AND measuredAt < ?
            """,
            (today_start, today_end),
        ).fetchall()
        return len(latest) + len(stats)

    def old_trend_summary() -> int:
        rows = db.execute(
            """
            SELECT avgSystolic, avgDiastolic, highRiskAlertTriggered
            FROM measurement_sessions
            WHERE measuredAt >= ? AND measuredAt < ?
            """,
            (BASE_MILLIS, trend_end),
        ).fetchall()
        if rows:
            _ = (
                sum(row[0] for row in rows) / len(rows),
                max(row[0] for row in rows),
                sum(row[2] for row in rows),
            )
        return len(rows)

    def new_trend_summary() -> int:
        return len(
            db.execute(
                """
                SELECT COUNT(*), AVG(avgSystolic), AVG(avgDiastolic),
                       MAX(avgSystolic), MAX(avgDiastolic),
                       MIN(avgSystolic), MIN(avgDiastolic),
                       SUM(CASE WHEN highRiskAlertTriggered = 1 THEN 1 ELSE 0 END)
                FROM measurement_sessions
                WHERE measuredAt >= ? AND measuredAt < ?
                """,
                (BASE_MILLIS, trend_end),
            ).fetchall()
        )

    scenarios = {
        "calendar_month": (old_month, new_month),
        "selected_day": (old_day, new_day),
        "dashboard": (old_dashboard, new_dashboard),
        "trend_summary": (old_trend_summary, new_trend_summary),
    }
    results = []
    for name, (old_operation, new_operation) in scenarios.items():
        old_ms, old_rows = timed(old_operation, repeats)
        new_ms, new_rows = timed(new_operation, repeats)
        results.append(
            Measurement(
                size=size,
                scenario=name,
                old_median_ms=round(old_ms, 4),
                new_median_ms=round(new_ms, 4),
                speedup=round(old_ms / new_ms, 2) if new_ms > 0 else None,
                old_rows=old_rows,
                new_rows=new_rows,
            )
        )
    db.close()
    return results


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sizes", nargs="+", type=int, default=[0, 100, 1_000, 10_000])
    parser.add_argument("--repeats", type=int, default=25)
    args = parser.parse_args()
    results = [
        result
        for size in args.sizes
        for result in benchmark_size(size, args.repeats)
    ]
    print(json.dumps([asdict(result) for result in results], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
