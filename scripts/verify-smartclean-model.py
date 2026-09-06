#!/usr/bin/env python3
"""Evaluate the shipped Smart Clean tiny candidate model against held-out hard negatives."""
from __future__ import annotations

import json
import re
from pathlib import Path

BUCKETS = 64
AD_THRESHOLD = 20
BODY_THRESHOLD = -12
STRONG_MARKERS = (
    "http://", "https://", "www.", ".com", ".net", ".cn", ".tw", ".hk",
    "最新网址", "备用网址", "请收藏本站", "请记住本站", "手机用户请访问",
    "关注公众号", "微信公众号", "本书来自", "更多精彩", "搜索书名", "请牢记域名",
    "最新網址", "備用網址", "請收藏本站", "請記住本站", "手機用戶請訪問",
    "關注公眾號", "本書來自", "更多精彩", "搜尋書名", "請牢記網域",
)
SPECIAL_HEADINGS = {"序章", "楔子", "前言", "序言", "后记", "後記", "尾声", "尾聲", "大结局", "大結局", "终章", "終章"}


def hash_bigram(first: str, second: str) -> int:
    value = 0x811C9DC5
    for character in (first, second):
        code = ord(character)
        value = ((value ^ (code & 0xFF)) * 0x01000193) & 0xFFFFFFFF
        value = ((value ^ ((code >> 8) & 0xFF)) * 0x01000193) & 0xFFFFFFFF
    return value & (BUCKETS - 1)


def source_weights(path: Path) -> list[int]:
    text = path.read_text(encoding="utf-8")
    match = re.search(r"private val weights = intArrayOf\((.*?)\)\n", text, re.S)
    if not match:
        raise SystemExit(f"cannot locate runtime weights in {path}")
    values = [int(value) for value in re.findall(r"-?\d+", match.group(1))]
    if len(values) != BUCKETS:
        raise SystemExit(f"runtime model must contain {BUCKETS} weights, found {len(values)}")
    return values


def load_rows(path: Path) -> list[tuple[str, str]]:
    output: list[tuple[str, str]] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.startswith("#"):
            continue
        try:
            label, text = raw.split("\t", 1)
        except ValueError as error:
            raise SystemExit(f"{path}:{number}: expected LABEL<TAB>text") from error
        if label not in {"AD", "BODY"} or not text.strip():
            raise SystemExit(f"{path}:{number}: invalid row")
        output.append((label, text.strip()))
    return output


def load_adversarial_matrix(path: Path) -> list[tuple[str, str]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("version") != 2:
        raise SystemExit(f"{path}: unsupported matrix version")
    domains = payload.get("domains") or []
    contexts = payload.get("contexts") or []
    strong_ad = payload.get("strongAdTemplates") or []
    borderline_ad = payload.get("borderlineAd") or []
    body_templates = payload.get("bodyTemplates") or []
    if len(domains) < 8 or len(contexts) < 2 or len(strong_ad) < 5 or len(body_templates) < 20:
        raise SystemExit(f"{path}: adversarial matrix coverage became too small")

    output: list[tuple[str, str]] = []
    for template in strong_ad:
        for domain in domains:
            output.append(("AD", template.format(domain=domain)))
    for text in borderline_ad:
        for context in contexts:
            output.append(("AD", f"{text} {context}"))
    for template in body_templates:
        for domain in domains:
            # Two independent prose contexts make marker-in-body hard negatives substantially
            # larger than the positive set without training on these exact strings.
            for context in contexts[:2]:
                output.append(("BODY", f"{template.format(domain=domain)} {context}"))
    return output


def looks_like_heading(value: str) -> bool:
    if value.lower().startswith("chapter"):
        return True
    if value.startswith("第") and any(marker in value[:24] for marker in ("章", "回", "节", "節", "卷")):
        return True
    return value in SPECIAL_HEADINGS


def classify(text: str, weights: list[int]) -> tuple[str, int]:
    value = text.strip()[:512]
    if len(value) < 4:
        return "UNCERTAIN", 0
    score = -4
    lower = value.lower()
    for marker in STRONG_MARKERS:
        if marker.lower() in lower:
            score += 12
    seen: set[int] = set()
    chars = "".join(character for character in value if not character.isspace())
    for index in range(max(0, len(chars) - 1)):
        slot = hash_bigram(chars[index], chars[index + 1])
        if slot not in seen:
            seen.add(slot)
            score += weights[slot]
    if len(value) > 180:
        score -= 4
    if "。" in value and not any(marker.lower() in lower for marker in STRONG_MARKERS):
        score -= 4
    if looks_like_heading(value):
        score -= 20
    if score >= AD_THRESHOLD:
        return "AD", score
    if score <= BODY_THRESHOLD:
        return "BODY", score
    return "UNCERTAIN", score


def main() -> int:
    source = Path("apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt")
    weights = source_weights(source)
    if any(weight < -8 or weight > 8 for weight in weights):
        raise SystemExit("runtime weights must be bounded signed-int8-style values")

    rows = load_rows(Path("quality/smartclean/eval-v1.tsv"))
    rows += load_adversarial_matrix(Path("quality/smartclean/eval-v2-matrix.json"))
    ad_rows = sum(label == "AD" for label, _ in rows)
    body_rows = sum(label == "BODY" for label, _ in rows)
    if len(rows) < 500 or ad_rows < 100 or body_rows < 250:
        raise SystemExit(
            f"Smart Clean held-out corpus too small: total={len(rows)} AD={ad_rows} BODY={body_rows}"
        )

    true_ad = ad_rows
    decisions = [(label, text, *classify(text, weights)) for label, text in rows]
    predicted_ad = [item for item in decisions if item[2] == "AD"]
    true_positive = sum(item[0] == "AD" for item in predicted_ad)
    false_positive = [item for item in predicted_ad if item[0] != "AD"]
    precision = 1.0 if not predicted_ad else true_positive / len(predicted_ad)
    recall = 0.0 if true_ad == 0 else true_positive / true_ad

    if false_positive:
        for _, text, label, score in false_positive:
            print(f"FALSE POSITIVE {label}/{score}: {text}")
        raise SystemExit("Smart Clean auto-AD hard-negative false positive detected")
    if precision < 0.995:
        raise SystemExit(f"Smart Clean auto-AD precision too low: {precision:.4f}")
    if recall < 0.20:
        raise SystemExit(f"Smart Clean model became trivial/inert: AD recall {recall:.4f} < 0.20")

    headings = [item for item in decisions if item[0] == "BODY" and looks_like_heading(item[1])]
    unsafe_headings = [item for item in headings if item[2] == "AD"]
    if unsafe_headings:
        raise SystemExit(f"chapter heading classified as AD: {unsafe_headings}")

    runtime = source.read_text(encoding="utf-8")
    if "classifyCandidate(text: String)" not in runtime or "take(512)" not in runtime:
        raise SystemExit("candidate-only bounded semantic contract missing")
    if re.search(r"File\(|ReaderController|normalizedFile|documentFile", runtime):
        raise SystemExit("semantic classifier must never open or receive a whole document")

    print(
        "Smart Clean model quality OK: "
        f"rows={len(rows)} AD={ad_rows} BODY={body_rows} "
        f"precision={precision:.3f} recall={recall:.3f} false_positive=0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
