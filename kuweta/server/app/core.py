"""Baza danych i logika dyżurów kuwety."""
from __future__ import annotations

import json
import os
import random
import secrets
import sqlite3
from datetime import date, datetime, timedelta
from typing import Callable
from zoneinfo import ZoneInfo

from . import schedule
from .messages import DEFAULT_MESSAGES, SEED_VERSION

TZ = ZoneInfo(os.environ.get("KUWETA_TZ", "Europe/Warsaw"))
MAX_USERS = 2

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    is_admin INTEGER NOT NULL DEFAULT 0,
    settings TEXT NOT NULL DEFAULT '{}',
    notify_interval_min INTEGER NOT NULL DEFAULT 30,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS household (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    interval_days INTEGER NOT NULL DEFAULT 2,
    max_level INTEGER NOT NULL DEFAULT 4,
    assignee_id INTEGER,
    due_date TEXT,
    extended INTEGER NOT NULL DEFAULT 0,
    partner_alerted_on TEXT,
    last_reminder_at TEXT,
    reminders_day TEXT,
    reminders_today INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cleanings (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL,
    assignee_id INTEGER,
    at TEXT NOT NULL,
    due_date TEXT,
    extensions INTEGER NOT NULL DEFAULT 0,
    on_time INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS extensions (
    id INTEGER PRIMARY KEY,
    requester_id INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    decided_at TEXT
);
CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY,
    category TEXT NOT NULL,
    level INTEGER NOT NULL DEFAULT 0,
    text TEXT NOT NULL,
    builtin INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL,
    payload TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT
);
CREATE TABLE IF NOT EXISTS sounds (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    mime TEXT NOT NULL,
    ext TEXT NOT NULL,
    size INTEGER NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS pair_codes (
    code TEXT PRIMARY KEY,
    user_id INTEGER,
    expires_at TEXT NOT NULL
);
"""

CATEGORIES = ("reminder", "extension", "partner", "cleaned", "ext_request")
LEVEL_TITLES = {1: "Kuweta", 2: "Kuweta!", 3: "KUWETA!!", 4: "🚨 KUWETA 🚨"}


class ApiError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


def now() -> datetime:
    return datetime.now(TZ)


def connect(path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.executescript(SCHEMA)
    conn.execute("INSERT OR IGNORE INTO household (id) VALUES (1)")
    migrate(conn)
    return conn


def migrate(conn: sqlite3.Connection) -> None:
    cols = {r["name"] for r in conn.execute("PRAGMA table_info(household)")}
    if "cats" not in cols:
        conn.execute("ALTER TABLE household ADD COLUMN cats TEXT NOT NULL DEFAULT '[]'")
    if "last_used_at" not in {r["name"] for r in conn.execute("PRAGMA table_info(messages)")}:
        conn.execute("ALTER TABLE messages ADD COLUMN last_used_at TEXT")
    for col in ("sound_reminder", "sound_partner"):
        if col not in cols:
            conn.execute(f"ALTER TABLE household ADD COLUMN {col} INTEGER")
    if "seed_version" not in cols:
        conn.execute("ALTER TABLE household ADD COLUMN seed_version INTEGER NOT NULL DEFAULT 0")
    version = conn.execute("SELECT seed_version FROM household WHERE id = 1").fetchone()[0]
    if version < SEED_VERSION:
        # Nowa pula wbudowanych tekstów. Teksty dopisane przez admina (builtin = 0) zostają.
        conn.execute("DELETE FROM messages WHERE builtin = 1")
        conn.executemany(
            "INSERT INTO messages (category, level, text, builtin) VALUES (?, ?, ?, 1)", DEFAULT_MESSAGES
        )
        conn.execute("UPDATE household SET seed_version = ? WHERE id = 1", (SEED_VERSION,))


# ---------- zdarzenia (push przez SSE) ----------

class Bus:
    """Zapisuje zdarzenia w bazie i budzi otwarte połączenia SSE."""

    def __init__(self, conn: sqlite3.Connection):
        self.conn = conn
        self.listeners: list[Callable[[int], None]] = []

    def publish(self, user_id: int, payload: dict, ttl_min: int | None = None) -> None:
        t = now()
        expires = (t + timedelta(minutes=ttl_min)).isoformat() if ttl_min else None
        self.conn.execute(
            "INSERT INTO events (user_id, payload, created_at, expires_at) VALUES (?, ?, ?, ?)",
            (user_id, json.dumps(payload, ensure_ascii=False), t.isoformat(), expires),
        )
        for listener in self.listeners:
            listener(user_id)

    def notify(self, user_id: int, kind: str, title: str, body: str, ttl_min: int | None = None, **extra) -> None:
        self.publish(user_id, {"type": "notify", "kind": kind, "title": title, "body": body, **extra}, ttl_min)

    def state_changed(self) -> None:
        for row in self.conn.execute("SELECT id FROM users"):
            self.publish(row["id"], {"type": "state"}, ttl_min=60)

    def pending(self, user_id: int, after: int) -> list[sqlite3.Row]:
        return self.conn.execute(
            "SELECT id, payload FROM events WHERE user_id = ? AND id > ? "
            "AND (expires_at IS NULL OR expires_at > ?) ORDER BY id",
            (user_id, after, now().isoformat()),
        ).fetchall()

    def last_id(self, user_id: int) -> int:
        row = self.conn.execute("SELECT MAX(id) FROM events WHERE user_id = ?", (user_id,)).fetchone()
        return row[0] or 0

    def cleanup(self) -> None:
        cutoff = (now() - timedelta(days=3)).isoformat()
        self.conn.execute("DELETE FROM events WHERE created_at < ?", (cutoff,))


# ---------- użytkownicy ----------

def user_by_token(conn, token: str) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM users WHERE token = ?", (token,)).fetchone()
    if not row:
        raise ApiError(401, "Nieznane urządzenie. Sparuj aplikację ponownie.")
    return row


def users(conn) -> list[sqlite3.Row]:
    return conn.execute("SELECT * FROM users ORDER BY id").fetchall()


def partner_of(conn, user_id: int) -> sqlite3.Row | None:
    return conn.execute("SELECT * FROM users WHERE id != ? ORDER BY id LIMIT 1", (user_id,)).fetchone()


def settings_of(user: sqlite3.Row) -> dict:
    return schedule.normalize_settings(json.loads(user["settings"] or "{}"))


def register(conn, bus: Bus, name: str, code: str, admin_code: str) -> dict:
    name = name.strip()[:30]
    if not name:
        raise ApiError(400, "Podaj imię")
    code = code.strip().upper()
    token = secrets.token_urlsafe(32)
    t = now()
    existing = users(conn)

    if admin_code and code == admin_code.upper():
        admin = next((u for u in existing if u["is_admin"]), None)
        if admin:  # ponowne sparowanie telefonu admina
            conn.execute("UPDATE users SET token = ?, name = ? WHERE id = ?", (token, name, admin["id"]))
            return {"token": token, "user_id": admin["id"]}
        if len(existing) >= MAX_USERS:
            raise ApiError(409, "Komplet użytkowników")
        settings = schedule.normalize_settings({"cycle_start": t.date().isoformat()})
        cur = conn.execute(
            "INSERT INTO users (name, token, is_admin, settings, created_at) VALUES (?, ?, 1, ?, ?)",
            (name, token, json.dumps(settings), t.isoformat()),
        )
        return {"token": token, "user_id": cur.lastrowid}

    row = conn.execute(
        "SELECT * FROM pair_codes WHERE code = ? AND expires_at > ?", (code, t.isoformat())
    ).fetchone()
    if not row:
        raise ApiError(403, "Zły albo przeterminowany kod parowania")
    conn.execute("DELETE FROM pair_codes WHERE code = ?", (code,))

    if row["user_id"]:
        conn.execute("UPDATE users SET token = ?, name = ? WHERE id = ?", (token, name, row["user_id"]))
        return {"token": token, "user_id": row["user_id"]}
    if len(existing) >= MAX_USERS:
        raise ApiError(409, "Komplet użytkowników")
    settings = schedule.normalize_settings(
        {"cycle": schedule.PRESET_3ZM, "cycle_start": monday_of(t.date()).isoformat()}
    )
    cur = conn.execute(
        "INSERT INTO users (name, token, is_admin, settings, created_at) VALUES (?, ?, 0, ?, ?)",
        (name, token, json.dumps(settings), t.isoformat()),
    )
    if len(existing) + 1 == MAX_USERS:
        # Para gotowa: pierwszy dyżur ma admin, jutro.
        conn.execute(
            "UPDATE household SET assignee_id = ?, due_date = ? WHERE id = 1 AND assignee_id IS NULL",
            (existing[0]["id"], (t.date() + timedelta(days=1)).isoformat()),
        )
    bus.state_changed()
    return {"token": token, "user_id": cur.lastrowid}


def monday_of(d: date) -> date:
    return d - timedelta(days=d.weekday())


def new_pair_code(conn, user_id: int | None) -> dict:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    code = "".join(secrets.choice(alphabet) for _ in range(6))
    expires = now() + timedelta(hours=24)
    conn.execute("INSERT INTO pair_codes (code, user_id, expires_at) VALUES (?, ?, ?)", (code, user_id, expires.isoformat()))
    return {"code": code, "expires_at": expires.isoformat()}


# ---------- dyżur ----------

def household(conn) -> sqlite3.Row:
    return conn.execute("SELECT * FROM household WHERE id = 1").fetchone()


def cats(conn) -> list[str]:
    return json.loads(household(conn)["cats"] or "[]")


def set_cats(conn, names: list[str]) -> list[str]:
    clean_names = [n.strip()[:20] for n in names if n.strip()][:10]
    conn.execute("UPDATE household SET cats = ? WHERE id = 1", (json.dumps(clean_names, ensure_ascii=False),))
    return clean_names


def cats_phrase(names: list[str]) -> str:
    if not names:
        return "Koty"
    if len(names) == 1:
        return names[0]
    return ", ".join(names[:-1]) + " i " + names[-1]


def fmt(text: str, conn=None, **values) -> str:
    names = cats(conn) if conn is not None else []
    values.setdefault("cat", random.choice(names) if names else "Kot")
    values.setdefault("cats", cats_phrase(names))
    if "days" in values:
        d = int(values["days"])
        values.setdefault("ile_dni", f"{d} {'dzień' if d == 1 else 'dni'}")
    try:
        return text.format(**values)
    except (KeyError, IndexError, ValueError):
        return text


def pick_message(conn, category: str, level: int = 0, overdue: bool = True) -> str:
    """Losuje tekst, omijając ostatnio użyte, żeby się nie powtarzały.

    Przypomnienia biorą teksty z poziomu docelowego i jednego niżej (więcej różnorodności).
    """
    rows = conn.execute(
        "SELECT id, text, level, last_used_at FROM messages WHERE category = ? AND level <= ?",
        (category, max(level, 0)),
    ).fetchall()
    if not overdue:
        # Bez spóźnienia teksty o dniach spóźnienia nie mają sensu ("0 dni spóźnienia").
        rows = [r for r in rows if "{ile_dni}" not in r["text"] and "{days}" not in r["text"]] or rows
    if not rows:
        return "Kuweta!"
    top = max(r["level"] for r in rows)
    pool = [r for r in rows if r["level"] in (top, top - 1)] if category == "reminder" else rows
    # Najpierw nigdy nieużyte, potem najdawniej użyte. Losujemy z najstarszej połowy.
    pool.sort(key=lambda r: r["last_used_at"] or "")
    fresh = pool[: max(1, len(pool) // 2)]
    choice = random.choice(fresh)
    conn.execute("UPDATE messages SET last_used_at = ? WHERE id = ?", (now().isoformat(), choice["id"]))
    return choice["text"]

def reminder_level(overdue_days: int, sent_today: int) -> int:
    """Które przypomnienie dnia = poziom: 1., 2., 3., a od 4. już najostrzej.

    W dniu po terminie zaczynamy od razu od poziomu 3.
    """
    level = min(4, sent_today + 1)
    if overdue_days >= 1:
        level = max(level, 3)
    return level

def streak(conn, user_id: int) -> int:
    count = 0
    for row in conn.execute(
        "SELECT user_id, extensions, on_time FROM cleanings WHERE assignee_id = ? ORDER BY id DESC", (user_id,)
    ):
        if row["user_id"] != user_id or row["extensions"] or not row["on_time"]:
            break
        count += 1
    return count


def best_streak(conn, user_id: int) -> int:
    best = cur = 0
    for row in conn.execute(
        "SELECT user_id, extensions, on_time FROM cleanings WHERE assignee_id = ? ORDER BY id", (user_id,)
    ):
        if row["user_id"] == user_id and not row["extensions"] and row["on_time"]:
            cur += 1
            best = max(best, cur)
        else:
            cur = 0
    return best


def pending_extension(conn) -> sqlite3.Row | None:
    return conn.execute("SELECT * FROM extensions WHERE status = 'pending' ORDER BY id DESC LIMIT 1").fetchone()


def clean(conn, bus: Bus, user: sqlite3.Row) -> dict:
    h = household(conn)
    t = now()
    today = t.date()
    due = date.fromisoformat(h["due_date"]) if h["due_date"] else today
    on_time = today <= due
    conn.execute(
        "INSERT INTO cleanings (user_id, assignee_id, at, due_date, extensions, on_time) VALUES (?, ?, ?, ?, ?, ?)",
        (user["id"], h["assignee_id"], t.isoformat(), h["due_date"], h["extended"], int(on_time)),
    )
    partner = partner_of(conn, user["id"])
    next_assignee = partner["id"] if partner else user["id"]
    next_due = today + timedelta(days=h["interval_days"])
    conn.execute(
        "UPDATE household SET assignee_id = ?, due_date = ?, extended = 0, partner_alerted_on = NULL, "
        "last_reminder_at = NULL, reminders_today = 0 WHERE id = 1",
        (next_assignee, next_due.isoformat()),
    )
    conn.execute(
        "UPDATE extensions SET status = 'cancelled', decided_at = ? WHERE status = 'pending'", (t.isoformat(),)
    )
    if partner:
        body = fmt(pick_message(conn, "cleaned"), conn, name=user["name"], partner=partner["name"], days=h["interval_days"])
        bus.notify(partner["id"], "cleaned", "Kuweta ogarnięta ✔", body)
    bus.state_changed()
    return {"next_assignee_id": next_assignee, "next_due": next_due.isoformat(), "on_time": on_time}


def request_extension(conn, bus: Bus, user: sqlite3.Row) -> dict:
    h = household(conn)
    if h["assignee_id"] != user["id"]:
        raise ApiError(400, "To nie twój dyżur, nie masz czego przedłużać")
    if pending_extension(conn):
        raise ApiError(409, "Prośba już czeka na odpowiedź")
    partner = partner_of(conn, user["id"])
    if not partner:
        raise ApiError(400, "Brak drugiej osoby do zatwierdzenia")
    t = now()
    cur = conn.execute("INSERT INTO extensions (requester_id, created_at) VALUES (?, ?)", (user["id"], t.isoformat()))
    body = fmt(pick_message(conn, "ext_request"), conn, name=user["name"], partner=partner["name"])
    bus.notify(partner["id"], "ext_request", "Prośba o +1 dzień", body, extension_id=cur.lastrowid)
    bus.state_changed()
    return {"id": cur.lastrowid}


def decide_extension(conn, bus: Bus, user: sqlite3.Row, ext_id: int, approve: bool) -> dict:
    ext = conn.execute("SELECT * FROM extensions WHERE id = ?", (ext_id,)).fetchone()
    if not ext or ext["status"] != "pending":
        raise ApiError(404, "Ta prośba jest już nieaktualna")
    if ext["requester_id"] == user["id"]:
        raise ApiError(403, "Nie możesz zatwierdzić własnej prośby, cwaniaku")
    t = now()
    status = "approved" if approve else "rejected"
    conn.execute("UPDATE extensions SET status = ?, decided_at = ? WHERE id = ?", (status, t.isoformat(), ext_id))
    if approve:
        h = household(conn)
        base = max(date.fromisoformat(h["due_date"]), t.date()) if h["due_date"] else t.date()
        conn.execute(
            "UPDATE household SET due_date = ?, extended = extended + 1, partner_alerted_on = NULL WHERE id = 1",
            ((base + timedelta(days=1)).isoformat(),),
        )
        bus.notify(ext["requester_id"], "ext_decided", "Masz dzień zwłoki",
                   f"{user['name']} się zgodził(a). Jutro nie ma chuja we wsi.")
    else:
        bus.notify(ext["requester_id"], "ext_decided", "Odmowa",
                   f"{user['name']} mówi NIE. Łopatka w łapę i zapierdalaj, dziś.")
    bus.state_changed()
    return {"status": status}


# ---------- własne dźwięki ----------

SOUND_SLOTS = ("reminder", "partner")
SOUND_TYPES = {
    "audio/mpeg": "mp3", "audio/mp3": "mp3", "audio/ogg": "ogg", "application/ogg": "ogg",
    "audio/wav": "wav", "audio/x-wav": "wav", "audio/wave": "wav",
    "audio/mp4": "m4a", "audio/x-m4a": "m4a", "audio/aac": "aac", "audio/opus": "opus",
}
MAX_SOUND_BYTES = 2 * 1024 * 1024


def sounds_dir(db_path: str) -> str:
    path = os.path.join(os.path.dirname(os.path.abspath(db_path)), "sounds")
    os.makedirs(path, exist_ok=True)
    return path


def add_sound(conn, folder: str, name: str, mime: str, data: bytes) -> int:
    mime = mime.split(";")[0].strip().lower()
    ext = SOUND_TYPES.get(mime)
    if not ext:
        guess = name.rsplit(".", 1)[-1].lower() if "." in name else ""
        ext = guess if guess in set(SOUND_TYPES.values()) else None
    if not ext:
        raise ApiError(415, "Obsługiwane formaty: mp3, ogg, wav, m4a, aac, opus")
    if not data:
        raise ApiError(400, "Pusty plik")
    if len(data) > MAX_SOUND_BYTES:
        raise ApiError(413, "Plik za duży, limit to 2 MB (krótki dźwięk wystarczy)")
    name = (name.rsplit(".", 1)[0] if "." in name else name).strip()[:40] or "dźwięk"
    cur = conn.execute(
        "INSERT INTO sounds (name, mime, ext, size, created_at) VALUES (?, ?, ?, ?, ?)",
        (name, mime, ext, len(data), now().isoformat()),
    )
    with open(os.path.join(folder, f"{cur.lastrowid}.{ext}"), "wb") as f:
        f.write(data)
    return cur.lastrowid


def sound_row(conn, sound_id: int) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM sounds WHERE id = ?", (sound_id,)).fetchone()
    if not row:
        raise ApiError(404, "Nie ma takiego dźwięku")
    return row


def delete_sound(conn, folder: str, sound_id: int) -> None:
    row = sound_row(conn, sound_id)
    conn.execute("DELETE FROM sounds WHERE id = ?", (sound_id,))
    for slot in SOUND_SLOTS:
        conn.execute(f"UPDATE household SET sound_{slot} = NULL WHERE sound_{slot} = ?", (sound_id,))
    try:
        os.remove(os.path.join(folder, f"{row['id']}.{row['ext']}"))
    except FileNotFoundError:
        pass


def assign_sound(conn, slot: str, sound_id: int | None) -> None:
    if slot not in SOUND_SLOTS:
        raise ApiError(400, "Nieznany rodzaj powiadomienia")
    if sound_id is not None:
        sound_row(conn, sound_id)
    conn.execute(f"UPDATE household SET sound_{slot} = ? WHERE id = 1", (sound_id,))


def sound_info(conn) -> dict:
    h = household(conn)
    out = {}
    for slot in SOUND_SLOTS:
        sid = h[f"sound_{slot}"]
        row = conn.execute("SELECT id, name, ext FROM sounds WHERE id = ?", (sid,)).fetchone() if sid else None
        out[slot] = dict(row) if row else None
    return out


def list_sounds(conn) -> list[dict]:
    return [dict(r) for r in conn.execute("SELECT id, name, mime, ext, size FROM sounds ORDER BY id")]


# ---------- przypomnienia ----------

def tick(conn, bus: Bus, t: datetime | None = None) -> list[str]:
    """Wywoływane co minutę. Zwraca listę wysłanych powiadomień (do testów i logów)."""
    t = t or now()
    sent: list[str] = []
    h = household(conn)
    if not h["assignee_id"] or not h["due_date"]:
        return sent
    today = t.date()
    due = date.fromisoformat(h["due_date"])
    if today < due:
        return sent
    assignee = conn.execute("SELECT * FROM users WHERE id = ?", (h["assignee_id"],)).fetchone()
    if not assignee:
        return sent
    partner = partner_of(conn, assignee["id"])
    overdue = (today - due).days

    sent_today = h["reminders_today"] if h["reminders_day"] == today.isoformat() else 0
    last = datetime.fromisoformat(h["last_reminder_at"]) if h["last_reminder_at"] else None
    interval = timedelta(minutes=max(5, assignee["notify_interval_min"]))
    if schedule.can_notify(settings_of(assignee), t, TZ) and (last is None or t - last >= interval - timedelta(seconds=30)):
        level = min(reminder_level(overdue, sent_today), h["max_level"])
        category = "extension" if h["extended"] and random.random() < 0.5 else "reminder"
        values = dict(name=assignee["name"], partner=partner["name"] if partner else "", days=overdue)
        body = fmt(pick_message(conn, category, level, overdue=overdue >= 1), conn, **values)
        title = LEVEL_TITLES[level] + (" (po przedłużeniu)" if h["extended"] else "")
        bus.notify(assignee["id"], "reminder", title, body, ttl_min=20, level=level)
        conn.execute(
            "UPDATE household SET last_reminder_at = ?, reminders_day = ?, reminders_today = ? WHERE id = 1",
            (t.isoformat(), today.isoformat(), sent_today + 1),
        )
        sent.append(f"reminder:{assignee['id']}:{level}")

    if (
        partner
        and overdue >= 1
        and h["partner_alerted_on"] != today.isoformat()
        and schedule.can_notify(settings_of(partner), t, TZ)
    ):
        body = fmt(pick_message(conn, "partner"), conn, name=assignee["name"], partner=partner["name"], days=overdue)
        bus.notify(partner["id"], "partner", "Donos z kuwety", body, ttl_min=120)
        conn.execute("UPDATE household SET partner_alerted_on = ? WHERE id = 1", (today.isoformat(),))
        sent.append(f"partner:{partner['id']}")
    return sent


# ---------- widoki dla aplikacji ----------

def user_public(u: sqlite3.Row | None) -> dict | None:
    if not u:
        return None
    return {"id": u["id"], "name": u["name"], "is_admin": bool(u["is_admin"]), "notify_interval_min": u["notify_interval_min"]}


def state(conn, user: sqlite3.Row) -> dict:
    t = now()
    today = t.date()
    h = household(conn)
    partner = partner_of(conn, user["id"])
    by_id = {u["id"]: u for u in users(conn)}
    s = settings_of(user)

    duty = None
    if h["assignee_id"] and h["due_date"]:
        due = date.fromisoformat(h["due_date"])
        assignee = by_id.get(h["assignee_id"])
        nxt = partner_of(conn, h["assignee_id"]) or assignee
        last = conn.execute("SELECT at FROM cleanings ORDER BY id DESC LIMIT 1").fetchone()
        duty = {
            "assignee_id": h["assignee_id"],
            "assignee_name": assignee["name"] if assignee else "?",
            "is_me": h["assignee_id"] == user["id"],
            "due_date": h["due_date"],
            "days_left": (due - today).days,
            "extended": h["extended"],
            "interval_days": h["interval_days"],
            "next_name": nxt["name"] if nxt else "?",
            "next_in_days": max((due - today).days, 0) + h["interval_days"],
            "last_cleaned_at": last["at"] if last else None,
            "reminders_today": h["reminders_today"] if h["reminders_day"] == today.isoformat() else 0,
        }

    ext = pending_extension(conn)
    pending = None
    if ext:
        pending = {
            "id": ext["id"],
            "requester_id": ext["requester_id"],
            "requester_name": by_id[ext["requester_id"]]["name"] if ext["requester_id"] in by_id else "?",
            "created_at": ext["created_at"],
            "mine": ext["requester_id"] == user["id"],
        }

    upcoming = [
        {"date": (today + timedelta(days=i)).isoformat(), "code": schedule.shift_code(s, today + timedelta(days=i))}
        for i in range(14)
    ]
    return {
        "server_time": t.isoformat(),
        "me": user_public(user),
        "partner": user_public(partner),
        "duty": duty,
        "pending_extension": pending,
        "streaks": [
            {"user_id": u["id"], "name": u["name"], "current": streak(conn, u["id"]), "best": best_streak(conn, u["id"])}
            for u in by_id.values()
        ],
        "today": {
            "code": schedule.shift_code(s, today),
            "timeline": schedule.day_timeline(s, today, TZ),
            "can_notify_now": schedule.can_notify(s, t, TZ),
        },
        "upcoming": upcoming,
        "max_level": h["max_level"],
        "cats": cats(conn),
        "sounds": sound_info(conn),
    }


def ranking(conn, month: str) -> dict:
    rows = conn.execute("SELECT * FROM cleanings WHERE substr(at, 1, 7) = ? ORDER BY id DESC", (month,)).fetchall()
    exts = conn.execute(
        "SELECT requester_id, status FROM extensions WHERE substr(created_at, 1, 7) = ?", (month,)
    ).fetchall()
    out = []
    for u in users(conn):
        mine = [r for r in rows if r["user_id"] == u["id"]]
        out.append({
            "user_id": u["id"],
            "name": u["name"],
            "cleanings": len(mine),
            "on_time": sum(1 for r in mine if r["on_time"] and not r["extensions"]),
            "covers": sum(1 for r in mine if r["assignee_id"] not in (None, u["id"])),
            "extensions": sum(1 for e in exts if e["requester_id"] == u["id"] and e["status"] == "approved"),
            "current_streak": streak(conn, u["id"]),
            "best_streak": best_streak(conn, u["id"]),
        })
    out.sort(key=lambda x: (x["on_time"], x["cleanings"]), reverse=True)
    names = {u["id"]: u["name"] for u in users(conn)}
    history = [
        {"name": names.get(r["user_id"], "?"), "at": r["at"], "on_time": bool(r["on_time"]), "extensions": r["extensions"],
         "cover": r["assignee_id"] not in (None, r["user_id"])}
        for r in rows[:30]
    ]
    return {"month": month, "players": out, "history": history}
