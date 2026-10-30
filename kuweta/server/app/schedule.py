"""Grafik zmianowy: kiedy osoba pracuje, śpi i kiedy wolno ją męczyć powiadomieniami."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

DEFAULT_SHIFTS = {
    "R": ["06:00", "14:00"],
    "P": ["14:00", "22:00"],
    "N": ["22:00", "06:00"],
}

# Pięciobrygadówka: 2x rano, 2x popołudnie, 2x nocka, 4 dni wolne.
PRESET_5BRYG = ["R", "R", "P", "P", "N", "N", "-", "-", "-", "-"]
# Pn-pt, zmiana rotuje co tydzień: nocka -> popołudnie -> rano.
PRESET_3ZM = ["N"] * 5 + ["-", "-"] + ["P"] * 5 + ["-", "-"] + ["R"] * 5 + ["-", "-"]

DEFAULT_SETTINGS = {
    "cycle": PRESET_5BRYG,
    "cycle_start": None,  # data ISO; None = dziś przy pierwszym zapisie
    "shifts": DEFAULT_SHIFTS,
    "commute_min": 30,  # cisza przed zmianą (dojazd)
    "grace_min": 60,  # ile po robocie wolno jeszcze męczyć
    "sleep_after_night_h": 7,  # sen po nocce, liczony po okresie "grace"
    "quiet_start": "23:00",
    "quiet_end": "08:00",
    "overrides": {},  # {"2026-11-05": "-"}
}

SHIFT_NAMES = {"R": "rano", "P": "popołudnie", "N": "nocka", "-": "wolne"}


def parse_hm(value: str) -> time:
    h, m = value.split(":")
    return time(int(h), int(m))


def normalize_settings(raw: dict | None) -> dict:
    s = dict(DEFAULT_SETTINGS)
    s["shifts"] = dict(DEFAULT_SHIFTS)
    s["overrides"] = {}
    if raw:
        for key in DEFAULT_SETTINGS:
            if key in raw and raw[key] is not None:
                s[key] = raw[key]
        s["shifts"] = {**DEFAULT_SHIFTS, **(raw.get("shifts") or {})}
    return s


def validate_settings(s: dict) -> None:
    cycle = s["cycle"]
    if not isinstance(cycle, list) or not 1 <= len(cycle) <= 60:
        raise ValueError("Cykl musi mieć od 1 do 60 dni")
    for code in cycle:
        if code != "-" and code not in s["shifts"]:
            raise ValueError(f"Nieznana zmiana w cyklu: {code}")
    for code, (a, b) in s["shifts"].items():
        parse_hm(a), parse_hm(b)
    parse_hm(s["quiet_start"]), parse_hm(s["quiet_end"])
    if s["cycle_start"]:
        date.fromisoformat(s["cycle_start"])
    for d, code in s["overrides"].items():
        date.fromisoformat(d)
        if code != "-" and code not in s["shifts"]:
            raise ValueError(f"Nieznana zmiana w wyjątku: {code}")
    for key, hi in (("commute_min", 240), ("grace_min", 240), ("sleep_after_night_h", 14)):
        if not 0 <= int(s[key]) <= hi:
            raise ValueError(f"{key} poza zakresem 0-{hi}")


def shift_code(s: dict, day: date) -> str:
    override = s["overrides"].get(day.isoformat())
    if override:
        return override
    start = date.fromisoformat(s["cycle_start"]) if s["cycle_start"] else day
    cycle = s["cycle"]
    return cycle[(day - start).days % len(cycle)]


@dataclass
class Windows:
    work: list[tuple[datetime, datetime]]
    grace: list[tuple[datetime, datetime]]
    sleep: list[tuple[datetime, datetime]]


def _windows(s: dict, around: date, tz: ZoneInfo) -> Windows:
    w = Windows([], [], [])
    commute = timedelta(minutes=int(s["commute_min"]))
    grace = timedelta(minutes=int(s["grace_min"]))
    night_sleep = timedelta(hours=int(s["sleep_after_night_h"]))
    for offset in (-2, -1, 0, 1):
        day = around + timedelta(days=offset)
        code = shift_code(s, day)
        if code == "-":
            continue
        a, b = (parse_hm(x) for x in s["shifts"][code])
        start = datetime.combine(day, a, tz)
        end = datetime.combine(day, b, tz)
        if end <= start:
            end += timedelta(days=1)
        w.work.append((start - commute, end))
        w.grace.append((end, end + grace))
        # Po nocce osoba śpi. Po popołudniówce sen załatwiają godziny ciszy.
        if code == "N":
            w.sleep.append((end + grace, end + grace + night_sleep))
    return w


def _in(t: datetime, spans) -> bool:
    return any(a <= t < b for a, b in spans)


def _quiet(s: dict, t: datetime) -> bool:
    qs, qe = parse_hm(s["quiet_start"]), parse_hm(s["quiet_end"])
    now = t.timetz().replace(tzinfo=None)
    if qs == qe:
        return False
    if qs < qe:
        return qs <= now < qe
    return now >= qs or now < qe


def classify(s: dict, t: datetime, tz: ZoneInfo) -> str:
    """Zwraca 'work', 'sleep' albo 'free' dla danej chwili."""
    t = t.astimezone(tz)
    w = _windows(s, t.date(), tz)
    if _in(t, w.work):
        return "work"
    if _in(t, w.grace):
        return "free"
    if _in(t, w.sleep) or _quiet(s, t):
        return "sleep"
    return "free"


def can_notify(s: dict, t: datetime, tz: ZoneInfo) -> bool:
    return classify(s, t, tz) == "free"


def day_timeline(s: dict, day: date, tz: ZoneInfo) -> list[dict]:
    """Doba w 15-minutowych kawałkach, sklejona w odcinki do wykresu."""
    segments: list[dict] = []
    start = datetime.combine(day, time(0, 0), tz)
    for i in range(96):
        t = start + timedelta(minutes=15 * i)
        kind = classify(s, t, tz)
        label = f"{t.hour:02d}:{t.minute:02d}"
        if segments and segments[-1]["kind"] == kind:
            segments[-1]["minutes"] += 15
        else:
            segments.append({"from": label, "minutes": 15, "kind": kind})
    return segments
