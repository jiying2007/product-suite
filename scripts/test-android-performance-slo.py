#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("check-android-performance-slo.py")
STARTUP_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-startup-slo.py")
FIRST_READABLE_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-first-readable-slo.py")
NEW_IMPORT_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-new-import-slo.py")
CHAPTER_JUMP_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-chapter-jump-slo.py")
INDEXED_SEARCH_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-indexed-search-slo.py")
SMART_CLEAN_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-smart-clean-slo.py")
TTS_NEXT_CHUNK_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-tts-next-chunk-slo.py")
STABILITY_200_MIB_MODULE_PATH = pathlib.Path(__file__).with_name("check-android-200mib-stability-slo.py")
HOSTED_BASELINE_PATH = pathlib.Path(__file__).with_name("reader-hosted-emulator-baseline.json")
PHYSICAL_RUNNER_PATH = pathlib.Path(__file__).with_name("run-android-physical-release-performance.sh")
spec = importlib.util.spec_from_file_location("jingdu_android_performance_slo", MODULE_PATH)
assert spec and spec.loader
slo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(slo)
startup_spec = importlib.util.spec_from_file_location("jingdu_android_startup_slo", STARTUP_MODULE_PATH)
assert startup_spec and startup_spec.loader
startup_slo = importlib.util.module_from_spec(startup_spec)
startup_spec.loader.exec_module(startup_slo)
first_readable_spec = importlib.util.spec_from_file_location(
    "jingdu_android_first_readable_slo", FIRST_READABLE_MODULE_PATH
)
assert first_readable_spec and first_readable_spec.loader
first_readable_slo = importlib.util.module_from_spec(first_readable_spec)
sys.modules[first_readable_spec.name] = first_readable_slo
first_readable_spec.loader.exec_module(first_readable_slo)
new_import_spec = importlib.util.spec_from_file_location(
    "jingdu_android_new_import_slo", NEW_IMPORT_MODULE_PATH
)
assert new_import_spec and new_import_spec.loader
new_import_slo = importlib.util.module_from_spec(new_import_spec)
sys.modules[new_import_spec.name] = new_import_slo
new_import_spec.loader.exec_module(new_import_slo)
chapter_jump_spec = importlib.util.spec_from_file_location(
    "jingdu_android_chapter_jump_slo", CHAPTER_JUMP_MODULE_PATH
)
assert chapter_jump_spec and chapter_jump_spec.loader
chapter_jump_slo = importlib.util.module_from_spec(chapter_jump_spec)
sys.modules[chapter_jump_spec.name] = chapter_jump_slo
chapter_jump_spec.loader.exec_module(chapter_jump_slo)
indexed_search_spec = importlib.util.spec_from_file_location(
    "jingdu_android_indexed_search_slo", INDEXED_SEARCH_MODULE_PATH
)
assert indexed_search_spec and indexed_search_spec.loader
indexed_search_slo = importlib.util.module_from_spec(indexed_search_spec)
sys.modules[indexed_search_spec.name] = indexed_search_slo
indexed_search_spec.loader.exec_module(indexed_search_slo)
smart_clean_spec = importlib.util.spec_from_file_location(
    "jingdu_android_smart_clean_slo", SMART_CLEAN_MODULE_PATH
)
assert smart_clean_spec and smart_clean_spec.loader
smart_clean_slo = importlib.util.module_from_spec(smart_clean_spec)
sys.modules[smart_clean_spec.name] = smart_clean_slo
smart_clean_spec.loader.exec_module(smart_clean_slo)
tts_next_chunk_spec = importlib.util.spec_from_file_location(
    "jingdu_android_tts_next_chunk_slo", TTS_NEXT_CHUNK_MODULE_PATH
)
assert tts_next_chunk_spec and tts_next_chunk_spec.loader
tts_next_chunk_slo = importlib.util.module_from_spec(tts_next_chunk_spec)
sys.modules[tts_next_chunk_spec.name] = tts_next_chunk_slo
tts_next_chunk_spec.loader.exec_module(tts_next_chunk_slo)
stability_200_mib_spec = importlib.util.spec_from_file_location(
    "jingdu_android_200mib_stability_slo", STABILITY_200_MIB_MODULE_PATH
)
assert stability_200_mib_spec and stability_200_mib_spec.loader
stability_200_mib_slo = importlib.util.module_from_spec(stability_200_mib_spec)
sys.modules[stability_200_mib_spec.name] = stability_200_mib_slo
stability_200_mib_spec.loader.exec_module(stability_200_mib_slo)


class AndroidPerformanceSloTest(unittest.TestCase):
    def test_androidx_percentile_matches_linear_interpolation(self) -> None:
        values = [float(value) for value in range(1, 101)]
        self.assertAlmostEqual(95.05, slo.androidx_percentile(values, 95))
        self.assertAlmostEqual(99.01, slo.androidx_percentile(values, 99))

    def test_cold_start_metric_uses_real_androidx_shape(self) -> None:
        payload = {
            "benchmarks": [
                {
                    "name": "coldStartup",
                    "className": "com.junchen.jingdu.macrobenchmark.StartupBenchmark",
                    "metrics": {
                        "timeToInitialDisplayMs": {
                            "runs": [620.0, 640.0, 660.0, 680.0, 700.0, 720.0, 740.0, 760.0, 780.0, 800.0]
                        }
                    },
                    "sampledMetrics": {},
                }
            ]
        }
        rows = startup_slo.cold_start_records(payload)
        self.assertEqual(1, len(rows))
        self.assertTrue(rows[0][0].endswith("StartupBenchmark.coldStartup"))
        self.assertEqual(10, len(rows[0][1]))
        self.assertAlmostEqual(791.0, startup_slo.androidx_percentile(rows[0][1], 95))

    def test_cold_start_cli_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "StartupBenchmark-benchmarkData.json"
            payload = {
                "benchmarks": [
                    {
                        "name": "coldStartup",
                        "className": "com.junchen.jingdu.macrobenchmark.StartupBenchmark",
                        "metrics": {"timeToInitialDisplayMs": {"runs": [700.0] * 10}},
                    }
                ]
            }
            path.write_text(json.dumps(payload), encoding="utf-8")
            passed = subprocess.run(
                [sys.executable, str(STARTUP_MODULE_PATH), str(path)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            self.assertIn("P95: 700.000ms", passed.stdout)

            payload["benchmarks"][0]["metrics"]["timeToInitialDisplayMs"]["runs"] = [1200.0] * 10
            path.write_text(json.dumps(payload), encoding="utf-8")
            slow = subprocess.run(
                [sys.executable, str(STARTUP_MODULE_PATH), str(path)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, slow.returncode, slow.stdout)
            self.assertIn("FAIL", slow.stdout)

            payload["benchmarks"][0]["metrics"]["timeToInitialDisplayMs"]["runs"] = [700.0] * 9
            path.write_text(json.dumps(payload), encoding="utf-8")
            truncated = subprocess.run(
                [sys.executable, str(STARTUP_MODULE_PATH), str(path)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, truncated.returncode, truncated.stdout)
            self.assertIn("minimum=10", truncated.stdout)

    def test_sampled_metrics_are_flattened_per_benchmark(self) -> None:
        payload = {
            "benchmarks": [
                {
                    "name": "pageTurn10MiB",
                    "className": "ReaderJourneyBenchmark",
                    "sampledMetrics": {"frameDurationCpuMs": {"runs": [[5.0, 10.0], [15.0, 20.0]]}},
                }
            ]
        }
        rows = slo.frame_sample_sets(payload)
        self.assertEqual([("ReaderJourneyBenchmark.pageTurn10MiB", [5.0, 10.0, 15.0, 20.0])], rows)

    def test_required_interaction_sample_counts_reject_truncated_evidence(self) -> None:
        valid = [
            ("ReaderJourneyBenchmark.pageTurn10MiB", [1.0] * 20),
            ("ReaderJourneyBenchmark.continuousScroll10MiB", [1.0] * 500),
            ("ReaderJourneyBenchmark.chaptersAndSettings10MiB", [1.0] * 50),
        ]
        self.assertEqual([], slo.required_sample_failures(valid))
        truncated = [
            ("ReaderJourneyBenchmark.pageTurn10MiB", [1.0] * 20),
            ("ReaderJourneyBenchmark.continuousScroll10MiB", [1.0] * 2),
        ]
        failures = slo.required_sample_failures(truncated)
        self.assertTrue(any("continuousScroll10MiB samples=2" in failure for failure in failures))
        self.assertTrue(any("missing required frame evidence: chaptersAndSettings10MiB" in failure for failure in failures))

    def test_release_defaults_remain_product_slo(self) -> None:
        self.assertEqual((40.0, 80.0), slo.resolve_limits("release", None, None))
        self.assertEqual(40.0, slo.RELEASE_P95_MS)
        self.assertEqual(80.0, slo.RELEASE_P99_MS)

    def test_hosted_defaults_are_separate_regression_ceiling(self) -> None:
        self.assertEqual((160.0, 220.0), slo.resolve_limits("hosted-regression", None, None))
        self.assertEqual(0.15, slo.HOSTED_MAX_REGRESSION_RATIO)

    def test_hosted_baseline_requires_complete_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "baseline.json"
            payload = {
                "schemaVersion": 1,
                "kind": "reader-hosted-emulator-regression-baseline",
                "benchmarks": {
                    "pageTurn10MiB": {"p95Ms": 80.0, "p99Ms": 110.0},
                    "continuousScroll10MiB": {"p95Ms": 95.0, "p99Ms": 130.0},
                    "chaptersAndSettings10MiB": {"p95Ms": 100.0, "p99Ms": 140.0},
                },
            }
            path.write_text(json.dumps(payload), encoding="utf-8")
            loaded = slo.load_hosted_baseline(str(path))
            self.assertEqual(95.0, loaded["continuousScroll10MiB"]["p95Ms"])
            del payload["benchmarks"]["chaptersAndSettings10MiB"]
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "missing benchmark"):
                slo.load_hosted_baseline(str(path))

    def test_checked_in_hosted_baseline_is_exact_evidence(self) -> None:
        loaded = slo.load_hosted_baseline(str(HOSTED_BASELINE_PATH))
        self.assertAlmostEqual(64.348030, loaded["pageTurn10MiB"]["p95Ms"])
        self.assertAlmostEqual(128.868375, loaded["continuousScroll10MiB"]["p95Ms"])
        self.assertAlmostEqual(187.723889, loaded["chaptersAndSettings10MiB"]["p99Ms"])
        payload = json.loads(HOSTED_BASELINE_PATH.read_text(encoding="utf-8"))
        self.assertEqual("fa22d088df7456330244ac4dc2c00a82da888656", payload["source"]["headSha"])
        self.assertEqual(9727262417, payload["source"]["artifactId"])
        self.assertEqual(59, payload["benchmarks"]["pageTurn10MiB"]["samples"])
        self.assertEqual(687, payload["benchmarks"]["continuousScroll10MiB"]["samples"])
        self.assertEqual(167, payload["benchmarks"]["chaptersAndSettings10MiB"]["samples"])

    def test_hosted_relative_limit_and_absolute_ceiling_both_apply(self) -> None:
        baseline = 100.0
        relative = baseline * (1.0 + slo.HOSTED_MAX_REGRESSION_RATIO)
        self.assertAlmostEqual(115.0, relative)
        self.assertAlmostEqual(115.0, min(slo.HOSTED_P95_MS, relative))
        high_baseline_relative = 150.0 * (1.0 + slo.HOSTED_MAX_REGRESSION_RATIO)
        self.assertEqual(160.0, min(slo.HOSTED_P95_MS, high_baseline_relative))

    def test_first_readable_cli_preserves_provenance_and_fails_closed(self) -> None:
        fixture_sha = "a" * 64
        source_sha = "b" * 40

        def evidence(count: int, duration: float) -> str:
            return "\n".join(
                (
                    "INSTRUMENTATION_STATUS: jingdu.firstReadableSample="
                    "metric=unchanged-imported-book;"
                    f"iteration={index};durationMs={duration + index:.3f};"
                    f"fixtureMiB=10;fixtureBytes=10485760;fixtureSha256={fixture_sha};"
                    f"position={index * 10};layoutGeneration={index}"
                )
                for index in range(1, count + 1)
            )

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "instrumentation.log"
            summary = root / "first-readable-slo.json"
            common = [
                sys.executable,
                str(FIRST_READABLE_MODULE_PATH),
                str(log),
                "--summary-json",
                str(summary),
                "--source-ref",
                "v2.3.11",
                "--source-sha",
                source_sha,
                "--manufacturer",
                "Example",
                "--model",
                "Physical Device",
                "--sdk",
                "36",
                "--fingerprint",
                "example/device/fingerprint",
            ]

            log.write_text(evidence(10, 300.0), encoding="utf-8")
            passed = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            payload = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload["pass"])
            self.assertEqual(source_sha, payload["sourceSha"])
            self.assertEqual(fixture_sha, payload["fixture"]["sha256"])
            self.assertEqual(10, len(payload["samplesMs"]))
            self.assertLess(payload["p95Ms"], 500.0)

            log.write_text(evidence(10, 600.0), encoding="utf-8")
            slow = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, slow.returncode, slow.stdout)
            self.assertIn("FAIL", slow.stdout)
            self.assertFalse(json.loads(summary.read_text(encoding="utf-8"))["pass"])

            log.write_text(evidence(9, 300.0), encoding="utf-8")
            truncated = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, truncated.returncode, truncated.stdout)
            self.assertIn("minimum=10", truncated.stdout)

    def test_new_20_mib_import_cli_requires_every_sample_under_target(self) -> None:
        fixture_sha = "c" * 64
        source_sha = "d" * 40

        def evidence(durations: list[float]) -> str:
            return "\n".join(
                (
                    "INSTRUMENTATION_STATUS: jingdu.newImportSample="
                    "metric=new-import-20mib;"
                    f"iteration={index};durationMs={duration:.3f};"
                    f"fixtureMiB=20;fixtureBytes=20971520;fixtureSha256={fixture_sha};"
                    f"position={index * 10};layoutGeneration={index}"
                )
                for index, duration in enumerate(durations, start=1)
            )

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "instrumentation.log"
            summary = root / "new-20mib-first-readable-slo.json"
            common = [
                sys.executable,
                str(NEW_IMPORT_MODULE_PATH),
                str(log),
                "--summary-json",
                str(summary),
                "--source-ref",
                "v2.3.11",
                "--source-sha",
                source_sha,
                "--manufacturer",
                "Example",
                "--model",
                "Physical Device",
                "--sdk",
                "36",
                "--fingerprint",
                "example/device/fingerprint",
            ]

            log.write_text(evidence([620.0, 640.0, 660.0, 680.0, 700.0]), encoding="utf-8")
            passed = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            payload = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload["pass"])
            self.assertEqual("every-retained-sample", payload["target"]["appliesTo"])
            self.assertEqual(5, len(payload["samplesMs"]))
            self.assertEqual(fixture_sha, payload["fixture"]["sha256"])
            self.assertLess(payload["maxMs"], 1000.0)

            log.write_text(evidence([620.0, 640.0, 1001.0, 680.0, 700.0]), encoding="utf-8")
            slow = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, slow.returncode, slow.stdout)
            self.assertIn("iteration=3", slow.stdout)
            self.assertFalse(json.loads(summary.read_text(encoding="utf-8"))["pass"])

            log.write_text(evidence([620.0, 640.0, 660.0, 680.0]), encoding="utf-8")
            truncated = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, truncated.returncode, truncated.stdout)
            self.assertIn("minimum=5", truncated.stdout)

    def test_new_100_mib_import_cli_filters_metric_and_requires_every_sample_under_target(self) -> None:
        fixture_sha = "e" * 64
        source_sha = "f" * 40

        def sample(metric: str, mib: int, index: int, duration: float) -> str:
            return (
                "INSTRUMENTATION_STATUS: jingdu.newImportSample="
                f"metric={metric};"
                f"iteration={index};durationMs={duration:.3f};"
                f"fixtureMiB={mib};fixtureBytes={mib * 1024 * 1024};fixtureSha256={fixture_sha};"
                f"position={index * 10};layoutGeneration={index}"
            )

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "instrumentation.log"
            summary = root / "new-100mib-first-readable-slo.json"
            common = [
                sys.executable,
                str(NEW_IMPORT_MODULE_PATH),
                str(log),
                "--fixture-mib",
                "100",
                "--limit-ms",
                "2000",
                "--summary-json",
                str(summary),
                "--source-ref",
                "v2.3.11",
                "--source-sha",
                source_sha,
                "--manufacturer",
                "Example",
                "--model",
                "Physical Device",
                "--sdk",
                "36",
                "--fingerprint",
                "example/device/fingerprint",
            ]

            lines = [
                sample("new-import-20mib", 20, index, 500.0 + index)
                for index in range(1, 6)
            ] + [
                sample("new-import-100mib", 100, index, 1200.0 + index * 10)
                for index in range(1, 6)
            ]
            log.write_text("\n".join(lines), encoding="utf-8")
            passed = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            payload = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload["pass"])
            self.assertEqual("new-import-100mib", payload["metric"])
            self.assertEqual(100, payload["fixture"]["mib"])
            self.assertEqual(5, len(payload["samplesMs"]))
            self.assertLess(payload["maxMs"], 2000.0)

            slow_lines = [
                sample("new-import-100mib", 100, index, 2100.0 if index == 4 else 1300.0 + index)
                for index in range(1, 6)
            ]
            log.write_text("\n".join(slow_lines), encoding="utf-8")
            slow = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, slow.returncode, slow.stdout)
            self.assertIn("iteration=4", slow.stdout)

    def test_chapter_jump_cli_enforces_p95_sample_floor_and_position_proof(self) -> None:
        fixture_sha = "1" * 64
        normalized_sha = "2" * 64
        source_sha = "3" * 40

        def evidence(count: int, duration: float, mismatch: bool = False) -> str:
            lines = []
            for index in range(1, count + 1):
                target = 1000 + index * 100
                final = target + 1 if mismatch and index == count else target
                lines.append(
                    "Result: Bundle[{sample="
                    "metric=chapter-jump;"
                    f"durationMs={duration + index:.3f};"
                    f"fixtureMiB=10;fixtureSha256={fixture_sha};"
                    f"normalizedSha256={normalized_sha};chapterCount=400;"
                    f"targetOffset={target};finalPosition={final}"
                    "}]"
                )
            return "\n".join(lines)

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "chapter-jump.log"
            summary = root / "chapter-jump-slo.json"
            common = [
                sys.executable,
                str(CHAPTER_JUMP_MODULE_PATH),
                str(log),
                "--summary-json",
                str(summary),
                "--source-ref",
                "v2.3.11",
                "--source-sha",
                source_sha,
                "--manufacturer",
                "Example",
                "--model",
                "Physical Device",
                "--sdk",
                "36",
                "--fingerprint",
                "example/device/fingerprint",
            ]

            log.write_text(evidence(10, 20.0), encoding="utf-8")
            passed = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            payload = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload["pass"])
            self.assertEqual("chapter-jump", payload["metric"])
            self.assertEqual(10, len(payload["samplesMs"]))
            self.assertLess(payload["p95Ms"], 100.0)
            self.assertTrue(all(item["targetOffset"] == item["finalPosition"] for item in payload["proof"]))

            log.write_text(evidence(10, 120.0), encoding="utf-8")
            slow = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, slow.returncode, slow.stdout)
            self.assertIn("P95=", slow.stdout)

            log.write_text(evidence(9, 20.0), encoding="utf-8")
            truncated = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, truncated.returncode, truncated.stdout)
            self.assertIn("minimum=10", truncated.stdout)

            log.write_text(evidence(10, 20.0, mismatch=True), encoding="utf-8")
            mismatch = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, mismatch.returncode, mismatch.stdout)
            self.assertIn("finalPosition", mismatch.stdout)

    def test_indexed_exact_search_cli_enforces_p95_sample_floor_and_hit_proof(self) -> None:
        fixture_sha = "4" * 64
        normalized_sha = "5" * 64
        source_sha = "6" * 40

        def evidence(count: int, duration: float, hits: int = 25) -> str:
            return "\n".join(
                (
                    "Result: Bundle[{sample="
                    "metric=indexed-exact-search;"
                    f"durationMs={duration + index:.3f};"
                    f"fixtureMiB=10;fixtureSha256={fixture_sha};"
                    f"normalizedSha256={normalized_sha};queryToken=quick_brown_fox;"
                    f"hitCount={hits};firstOffset={1000 + index}"
                    "}]"
                )
                for index in range(1, count + 1)
            )

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "indexed-search.log"
            summary = root / "indexed-search-slo.json"
            common = [
                sys.executable,
                str(INDEXED_SEARCH_MODULE_PATH),
                str(log),
                "--summary-json",
                str(summary),
                "--source-ref",
                "v2.3.11",
                "--source-sha",
                source_sha,
                "--manufacturer",
                "Example",
                "--model",
                "Physical Device",
                "--sdk",
                "36",
                "--fingerprint",
                "example/device/fingerprint",
            ]

            log.write_text(evidence(10, 20.0), encoding="utf-8")
            passed = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            payload = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload["pass"])
            self.assertEqual("indexed-exact-search", payload["metric"])
            self.assertEqual("quick_brown_fox", payload["fixture"]["queryToken"])
            self.assertEqual(10, len(payload["samplesMs"]))
            self.assertLess(payload["p95Ms"], 100.0)
            self.assertTrue(all(item["hitCount"] > 0 for item in payload["proof"]))

            log.write_text(evidence(10, 120.0), encoding="utf-8")
            slow = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, slow.returncode, slow.stdout)
            self.assertIn("P95=", slow.stdout)

            log.write_text(evidence(9, 20.0), encoding="utf-8")
            truncated = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, truncated.returncode, truncated.stdout)
            self.assertIn("minimum=10", truncated.stdout)

            log.write_text(evidence(10, 20.0, hits=0), encoding="utf-8")
            empty = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, empty.returncode, empty.stdout)
            self.assertIn("hitCount=0", empty.stdout)

    def test_smart_clean_cli_filters_size_and_requires_positive_candidates(self) -> None:
        fixture_sha = "7" * 64
        normalized_sha = "8" * 64
        source_sha = "9" * 40

        def sample(
            mib: int,
            index: int,
            duration: float,
            candidates: int = 12,
            fixture_bytes: int | None = None,
        ) -> str:
            return (
                "Result: Bundle[{sample="
                f"metric=smart-clean-{mib}mib;"
                f"durationMs={duration:.3f};"
                f"fixtureMiB={mib};fixtureBytes={fixture_bytes if fixture_bytes is not None else mib * 1024 * 1024};"
                f"fixtureSha256={fixture_sha};normalizedSha256={normalized_sha};"
                f"candidateCount={candidates};candidateSha256={'a' * 64};"
                "topScore=72;topReason=inline_fragment"
                "}]"
            )

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "smart-clean.log"
            summary = root / "smart-clean-slo.json"

            def run_gate(mib: int, limit: int) -> subprocess.CompletedProcess[str]:
                return subprocess.run(
                    [
                        sys.executable,
                        str(SMART_CLEAN_MODULE_PATH),
                        str(log),
                        "--fixture-mib",
                        str(mib),
                        "--limit-ms",
                        str(limit),
                        "--summary-json",
                        str(summary),
                        "--source-ref",
                        "v2.3.11",
                        "--source-sha",
                        source_sha,
                        "--manufacturer",
                        "Example",
                        "--model",
                        "Physical Device",
                        "--sdk",
                        "36",
                        "--fingerprint",
                        "example/device/fingerprint",
                    ],
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    check=False,
                )

            mixed = [
                sample(20, index, 600.0 + index * 10)
                for index in range(1, 6)
            ] + [
                sample(100, index, 2200.0 + index * 20)
                for index in range(1, 6)
            ]
            log.write_text("\n".join(mixed), encoding="utf-8")

            passed20 = run_gate(20, 1000)
            self.assertEqual(0, passed20.returncode, passed20.stdout)
            payload20 = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload20["pass"])
            self.assertEqual("smart-clean-20mib", payload20["metric"])
            self.assertEqual(20, payload20["fixture"]["mib"])
            self.assertEqual(5, len(payload20["samplesMs"]))
            self.assertTrue(all(item["candidateCount"] > 0 for item in payload20["proof"]))
            self.assertTrue(all(item["candidateSha256"] == "a" * 64 for item in payload20["proof"]))

            passed100 = run_gate(100, 3000)
            self.assertEqual(0, passed100.returncode, passed100.stdout)
            payload100 = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload100["pass"])
            self.assertEqual("smart-clean-100mib", payload100["metric"])
            self.assertEqual(100, payload100["fixture"]["mib"])
            self.assertLess(payload100["maxMs"], 3000.0)

            slow = [sample(100, index, 3100.0 if index == 3 else 2200.0) for index in range(1, 6)]
            log.write_text("\n".join(slow), encoding="utf-8")
            slow_result = run_gate(100, 3000)
            self.assertEqual(1, slow_result.returncode, slow_result.stdout)
            self.assertIn("sample=3", slow_result.stdout)

            truncated = [sample(20, index, 600.0) for index in range(1, 5)]
            log.write_text("\n".join(truncated), encoding="utf-8")
            truncated_result = run_gate(20, 1000)
            self.assertEqual(1, truncated_result.returncode, truncated_result.stdout)
            self.assertIn("minimum=5", truncated_result.stdout)

            empty = [sample(20, index, 600.0, candidates=0) for index in range(1, 6)]
            log.write_text("\n".join(empty), encoding="utf-8")
            empty_result = run_gate(20, 1000)
            self.assertEqual(1, empty_result.returncode, empty_result.stdout)
            self.assertIn("candidateCount=0", empty_result.stdout)

            undersized = [
                sample(20, index, 600.0, fixture_bytes=19 * 1024 * 1024)
                for index in range(1, 6)
            ]
            log.write_text("\n".join(undersized), encoding="utf-8")
            undersized_result = run_gate(20, 1000)
            self.assertEqual(1, undersized_result.returncode, undersized_result.stdout)
            self.assertIn("fixtureBytes=", undersized_result.stdout)
            self.assertIn("minimum=20971520", undersized_result.stdout)

    def test_tts_next_chunk_cli_enforces_real_queue_identity_and_p95(self) -> None:
        fixture_sha = "a" * 64
        normalized_sha = "b" * 64
        source_sha = "c" * 40

        def evidence(
            count: int,
            duration: float,
            voice: str = "en-us-x-test-local",
            final_next_offset: int | None = None,
        ) -> str:
            lines = []
            for index in range(1, count + 1):
                source_offset = (index - 1) * 900
                next_offset = source_offset + 900
                if final_next_offset is not None and index == count:
                    next_offset = final_next_offset
                lines.append(
                    "Result: Bundle[{sample="
                    "metric=tts-next-chunk;"
                    f"durationMs={duration + index:.3f};"
                    f"fixtureMiB=10;fixtureSha256={fixture_sha};"
                    f"normalizedSha256={normalized_sha};"
                    "engine=com.example.tts;"
                    f"voice={voice};locale=en-US;"
                    f"sourceOffset={source_offset};nextOffset={next_offset};"
                    "spokenUtf16Chars=320"
                    "}]"
                )
            return "\n".join(lines)

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "tts-next-chunk.log"
            summary = root / "tts-next-chunk-slo.json"
            common = [
                sys.executable,
                str(TTS_NEXT_CHUNK_MODULE_PATH),
                str(log),
                "--summary-json",
                str(summary),
                "--source-ref",
                "v2.3.11",
                "--source-sha",
                source_sha,
                "--manufacturer",
                "Example",
                "--model",
                "Physical Device",
                "--sdk",
                "36",
                "--fingerprint",
                "example/device/fingerprint",
            ]

            log.write_text(evidence(10, 60.0), encoding="utf-8")
            passed = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            payload = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload["pass"])
            self.assertEqual("tts-next-chunk", payload["metric"])
            self.assertEqual("com.example.tts", payload["tts"]["engine"])
            self.assertEqual("en-us-x-test-local", payload["tts"]["voice"])
            self.assertEqual("en-US", payload["tts"]["locale"])
            self.assertEqual(10, len(payload["samplesMs"]))
            self.assertLess(payload["p95Ms"], 150.0)
            self.assertTrue(all(item["nextOffset"] > item["sourceOffset"] for item in payload["proof"]))

            log.write_text(evidence(10, 180.0), encoding="utf-8")
            slow = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, slow.returncode, slow.stdout)
            self.assertIn("P95=", slow.stdout)

            log.write_text(evidence(9, 60.0), encoding="utf-8")
            truncated = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, truncated.returncode, truncated.stdout)
            self.assertIn("minimum=10", truncated.stdout)

            drift_lines = evidence(9, 60.0).splitlines()
            drift_lines.append(
                evidence(1, 60.0, voice="en-us-x-other-local").splitlines()[0]
            )
            log.write_text("\n".join(drift_lines), encoding="utf-8")
            drift = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, drift.returncode, drift.stdout)
            self.assertIn("identity drift", drift.stdout)

            log.write_text(evidence(10, 60.0, final_next_offset=8100), encoding="utf-8")
            stalled = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, stalled.returncode, stalled.stdout)
            self.assertIn("nextOffset", stalled.stdout)

    def test_200_mib_stability_cli_requires_completion_and_rejects_oom_anr(self) -> None:
        source_sha = "d" * 40

        def instrumentation(
            *,
            complete: bool = True,
            hits: int = 12,
            initial_generation: int = 7,
            final_generation: int = 9,
            fixture_bytes: int = 200 * 1024 * 1024,
        ) -> str:
            proof = (
                "INSTRUMENTATION_STATUS: jingdu.stability200MiB="
                f"fixtureMiB=200;fixtureBytes={fixture_bytes};"
                f"fixtureSha256={'e' * 64};normalizedSha256={'f' * 64};"
                "pid=4321;"
                "initialPosition=0;"
                f"initialLayoutGeneration={initial_generation};"
                "finalPosition=4096;"
                f"finalLayoutGeneration={final_generation};"
                f"searchHitCount={hits};"
                "cleanCandidateCount=0;"
                "operations=complete\n"
                if complete
                else ""
            )
            return proof + "INSTRUMENTATION_CODE: -1\n"

        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            log = root / "stability.log"
            logcat = root / "logcat.txt"
            meminfo = root / "meminfo.txt"
            exit_info = root / "exit-info.txt"
            summary = root / "stability-200mib-slo.json"
            meminfo.write_text(
                "** MEMINFO in pid 4321 [com.junchen.jingdu] **\nTOTAL PSS: 123456\n",
                encoding="utf-8",
            )
            exit_info.write_text("ACTIVITY MANAGER PROCESS EXIT INFO\n", encoding="utf-8")

            common = [
                sys.executable,
                str(STABILITY_200_MIB_MODULE_PATH),
                str(log),
                str(logcat),
                str(meminfo),
                str(exit_info),
                "--summary-json",
                str(summary),
                "--source-ref",
                "v2.3.11",
                "--source-sha",
                source_sha,
                "--manufacturer",
                "Example",
                "--model",
                "Physical Device",
                "--sdk",
                "36",
                "--fingerprint",
                "example/device/fingerprint",
            ]

            log.write_text(instrumentation(), encoding="utf-8")
            logcat.write_text("I/Jingdu: stability complete\n", encoding="utf-8")
            passed = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, passed.returncode, passed.stdout)
            payload = json.loads(summary.read_text(encoding="utf-8"))
            self.assertTrue(payload["pass"])
            self.assertEqual("200mib-open-search-clean-stability", payload["metric"])
            self.assertEqual(200, payload["proof"]["fixtureMiB"])
            self.assertEqual(209715200, payload["fixture"]["bytes"])
            self.assertEqual("e" * 64, payload["fixture"]["sourceSha256"])
            self.assertEqual("f" * 64, payload["fixture"]["normalizedSha256"])
            self.assertGreater(payload["proof"]["searchHitCount"], 0)
            self.assertEqual(0, payload["proof"]["cleanCandidateCount"])

            log.write_text(instrumentation(fixture_bytes=199 * 1024 * 1024), encoding="utf-8")
            undersized_200 = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, undersized_200.returncode, undersized_200.stdout)
            self.assertIn("fixtureBytes=", undersized_200.stdout)
            self.assertIn("minimum=209715200", undersized_200.stdout)
            log.write_text(instrumentation(), encoding="utf-8")

            logcat.write_text(
                "E/AndroidRuntime: FATAL EXCEPTION: main\n"
                "E/AndroidRuntime: Process: com.junchen.jingdu, PID: 4321\n"
                "E/AndroidRuntime: java.lang.OutOfMemoryError: Failed to allocate\n",
                encoding="utf-8",
            )
            oom = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, oom.returncode, oom.stdout)
            self.assertIn("target OutOfMemoryError", oom.stdout)

            logcat.write_text(
                "E/AndroidRuntime: FATAL EXCEPTION: main\n"
                "E/AndroidRuntime: Process: com.example.other, PID: 99\n"
                "E/AndroidRuntime: java.lang.OutOfMemoryError: unrelated process\n",
                encoding="utf-8",
            )
            unrelated = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, unrelated.returncode, unrelated.stdout)

            logcat.write_text(
                "E/ActivityManager: ANR in com.junchen.jingdu\n",
                encoding="utf-8",
            )
            anr = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, anr.returncode, anr.stdout)
            self.assertIn("target ANR", anr.stdout)

            log.write_text(instrumentation(complete=False), encoding="utf-8")
            logcat.write_text("I/Jingdu: no crash\n", encoding="utf-8")
            missing = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, missing.returncode, missing.stdout)
            self.assertIn("missing jingdu.stability200MiB", missing.stdout)

            log.write_text(instrumentation(hits=0), encoding="utf-8")
            no_hits = subprocess.run(
                common,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, no_hits.returncode, no_hits.stdout)
            self.assertIn("searchHitCount=0", no_hits.stdout)

    def test_physical_release_runner_is_shell_valid_and_release_only(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(PHYSICAL_RUNNER_PATH)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stdout)
        source = PHYSICAL_RUNNER_PATH.read_text(encoding="utf-8")
        self.assertIn("ro.kernel.qemu", source)
        self.assertIn("refuses emulator/generic devices", source)
        self.assertIn("-e jingdu.pageTurnInput physical-volume", source)
        self.assertIn("StartupBenchmark", source)
        self.assertIn("PhysicalReleaseSloBenchmark", source)
        self.assertIn('scripts/check-android-performance-slo.py "$RESULT_ROOT/evidence" --mode release', source)
        self.assertIn('scripts/check-android-startup-slo.py "$RESULT_ROOT/evidence"', source)
        self.assertIn('scripts/check-android-first-readable-slo.py "$LOG"', source)
        self.assertIn('--summary-json "$RESULT_ROOT/first-readable-slo.json"', source)
        self.assertIn('scripts/check-android-new-import-slo.py "$LOG"', source)
        self.assertIn('--summary-json "$RESULT_ROOT/new-20mib-first-readable-slo.json"', source)
        self.assertIn('--fixture-mib 100', source)
        self.assertIn('--limit-ms 2000', source)
        self.assertIn('--summary-json "$RESULT_ROOT/new-100mib-first-readable-slo.json"', source)
        self.assertIn('--method chapterJumpMetric', source)
        self.assertIn('scripts/check-android-chapter-jump-slo.py "$CHAPTER_JUMP_LOG"', source)
        self.assertIn('--summary-json "$RESULT_ROOT/chapter-jump-slo.json"', source)
        self.assertIn('--method indexedSearchMetric', source)
        self.assertIn('scripts/check-android-indexed-search-slo.py "$INDEXED_SEARCH_LOG"', source)
        self.assertIn('--summary-json "$RESULT_ROOT/indexed-search-slo.json"', source)
        self.assertIn('--method seedSmartClean --arg 20', source)
        self.assertIn('--method seedSmartClean --arg 100', source)
        self.assertIn('--method smartCleanMetric', source)
        self.assertIn('scripts/check-android-smart-clean-slo.py "$SMART_CLEAN_20_LOG"', source)
        self.assertIn('scripts/check-android-smart-clean-slo.py "$SMART_CLEAN_100_LOG"', source)
        self.assertIn('--summary-json "$RESULT_ROOT/smart-clean-20mib-slo.json"', source)
        self.assertIn('--summary-json "$RESULT_ROOT/smart-clean-100mib-slo.json"', source)
        self.assertIn('--method ttsNextChunkMetric', source)
        self.assertIn('scripts/check-android-tts-next-chunk-slo.py "$TTS_NEXT_CHUNK_LOG"', source)
        self.assertIn('--summary-json "$RESULT_ROOT/tts-next-chunk-slo.json"', source)
        self.assertIn('PhysicalReleaseStabilityTest', source)
        self.assertIn('scripts/check-android-200mib-stability-slo.py', source)
        self.assertIn('--summary-json "$RESULT_ROOT/stability-200mib-slo.json"', source)
        self.assertIn('stability-200mib-logcat.txt', source)
        self.assertIn('stability-200mib-meminfo.txt', source)
        self.assertIn('stability-200mib-exit-info.txt', source)
        self.assertIn('BENCHMARK_JSON', source)
        self.assertNotIn("androidx.benchmark.suppressErrors EMULATOR", source)

    def test_real_shape_file_discovery(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            report = root / "ReaderJourneyBenchmark-benchmarkData.json"
            report.write_text(json.dumps({"benchmarks": []}), encoding="utf-8")
            self.assertEqual([report], slo.collect_files([directory]))

    def test_reader_profile_product_contract(self) -> None:
        contract = pathlib.Path(__file__).with_name("verify-reader-profile-contract.py")
        result = subprocess.run(
            [sys.executable, str(contract)],
            cwd=contract.parent.parent,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("Reader Baseline/Startup Profile contract OK", result.stdout)


if __name__ == "__main__":
    unittest.main()
