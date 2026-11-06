"""Domowe: miesięczna składka, potwierdzanie przelewu, skarbonka i wydatki.

Kwoty trzymamy w eurocentach (int). Składka za miesiąc M jest do przelania od 24. dnia
miesiąca poprzedniego. Saldo = potwierdzone przelewy + korekty - wydatki, przechodzi
z miesiąca na miesiąc i może zejść na minus.
"""
from __future__ import annotations

import json
import random
import sqlite3
from datetime import date, datetime

from . import core, schedule
from .core import ApiError, Bus

DUE_DAY = 24
DEFAULT_CATEGORIES = ["Żarcie", "Żwirek", "Karma", "Chemia", "Apteka", "Inne"]
MAX_CATEGORIES = 8

SCHEMA = """
CREATE TABLE IF NOT EXISTS money_transfers (
    id INTEGER PRIMARY KEY,
    period TEXT NOT NULL UNIQUE,
    amount INTEGER NOT NULL,
    sent_at TEXT NOT NULL,
    confirmed_at TEXT
);
CREATE TABLE IF NOT EXISTS money_expenses (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    category TEXT NOT NULL DEFAULT '',
    note TEXT NOT NULL DEFAULT '',
    at TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS money_adjustments (
    id INTEGER PRIMARY KEY,
    amount INTEGER NOT NULL,
    at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS money_proposals (
    id INTEGER PRIMARY KEY,
    kind TEXT NOT NULL,
    value INTEGER NOT NULL,
    proposer_id INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    decided_at TEXT
);
"""

HOUSEHOLD_COLUMNS = (
    "money_payer_id INTEGER",
    "money_amount INTEGER NOT NULL DEFAULT 0",
    "money_start_period TEXT",
    "money_categories TEXT",
    "money_payer_reminded_on TEXT",
    "money_receiver_reminded_on TEXT",
)

PAYER_REMINDERS = [
    "{name}, 24. minął, a składki ani widu, ani słychu. Przelew, kurwa, {amount}.",
    "{cats} pytają, za co mają żreć w przyszłym miesiącu. Przelej {amount}, {name}.",
    "Skarbonka pusta jak twoje obietnice, {name}. {amount} na konto, już.",
    "{name}, kliknij „Przelane”, jak już przelejesz {amount}. Bo na razie to chuj, nie przelew.",
    "Dzień {day} miesiąca, a {name} dalej trzyma hajs w gaciach. {amount}, dawaj.",
    "{partner} czeka na {amount}. {cat} też. Ruszaj dupę do bankowości, {name}.",
]


def _money_now() -> datetime:
    return core.now()


def migrate(conn: sqlite3.Connection) -> None:
    conn.executescript(SCHEMA)
    cols = {r["name"] for r in conn.execute("PRAGMA table_info(household)")}
    for col in HOUSEHOLD_COLUMNS:
        if col.split()[0] not in cols:
            conn.execute(f"ALTER TABLE household ADD COLUMN {col}")


# ---------- okresy ----------

def add_months(period: str, n: int) -> str:
    y, m = map(int, period.split("-"))
    m += n
    y += (m - 1) // 12
    m = (m - 1) % 12 + 1
    return f"{y:04d}-{m:02d}"


def period_of(d: date) -> str:
    return f"{d.year:04d}-{d.month:02d}"


def current_period(today: date) -> str:
    """Okres, za który teraz się płaci: od 24. już za następny miesiąc."""
    p = period_of(today)
    return add_months(p, 1) if today.day >= DUE_DAY else p


def due_date(period: str) -> date:
    y, m = map(int, add_months(period, -1).split("-"))
    return date(y, m, DUE_DAY)


def fmt_eur(cents: int) -> str:
    sign = "-" if cents < 0 else ""
    cents = abs(cents)
    whole, rest = divmod(cents, 100)
    text = f"{whole:,}".replace(",", " ")
    return f"{sign}{text},{rest:02d} €" if rest else f"{sign}{text} €"


# ---------- odczyt ----------

def roles(conn) -> tuple[sqlite3.Row | None, sqlite3.Row | None]:
    h = core.household(conn)
    if not h["money_payer_id"]:
        return None, None
    payer = conn.execute("SELECT * FROM users WHERE id = ?", (h["money_payer_id"],)).fetchone()
    receiver = core.partner_of(conn, payer["id"]) if payer else None
    return payer, receiver


def categories(conn) -> list[str]:
    raw = core.household(conn)["money_categories"]
    return json.loads(raw) if raw else list(DEFAULT_CATEGORIES)


def balance(conn) -> int:
    got = conn.execute("SELECT COALESCE(SUM(amount), 0) FROM money_transfers WHERE confirmed_at IS NOT NULL").fetchone()[0]
    adj = conn.execute("SELECT COALESCE(SUM(amount), 0) FROM money_adjustments").fetchone()[0]
    spent = conn.execute("SELECT COALESCE(SUM(amount), 0) FROM money_expenses WHERE deleted = 0").fetchone()[0]
    return got + adj - spent


def transfer_for(conn, period: str) -> sqlite3.Row | None:
    return conn.execute("SELECT * FROM money_transfers WHERE period = ?", (period,)).fetchone()


def open_period(conn, today: date) -> str | None:
    """Najstarszy okres bez potwierdzonego przelewu (do bieżącego włącznie) albo następny, jeśli wszystko gra."""
    start = core.household(conn)["money_start_period"]
    if not start:
        return None
    cur = current_period(today)
    p = start
    while p <= cur:
        t = transfer_for(conn, p)
        if not t or not t["confirmed_at"]:
            return p
        p = add_months(p, 1)
    return p


def pending_proposals(conn) -> list[sqlite3.Row]:
    return conn.execute("SELECT * FROM money_proposals WHERE status = 'pending' ORDER BY id").fetchall()


def state(conn, user: sqlite3.Row) -> dict:
    payer, receiver = roles(conn)
    h = core.household(conn)
    today = _money_now().date()
    out: dict = {
        "configured": bool(payer and receiver),
        "categories": categories(conn),
        "amount_cents": h["money_amount"],
    }
    if not out["configured"]:
        return out
    period = open_period(conn, today)
    t = transfer_for(conn, period)
    if t and t["confirmed_at"]:
        status = "confirmed"
    elif t:
        status = "sent"
    elif due_date(period) <= today:
        status = "due"
    else:
        status = "not_due"
    month_start = f"{period_of(today)}-01"
    names = {u["id"]: u["name"] for u in core.users(conn)}
    out.update({
        "payer": core.user_public(payer),
        "receiver": core.user_public(receiver),
        "role": "payer" if user["id"] == payer["id"] else "receiver",
        "balance_cents": balance(conn),
        "period": period,
        "period_status": status,
        "due_date": due_date(period).isoformat(),
        "transfer_id": t["id"] if t else None,
        "transfer_cents": t["amount"] if t else h["money_amount"],
        "spent_month_cents": conn.execute(
            "SELECT COALESCE(SUM(amount), 0) FROM money_expenses WHERE deleted = 0 AND at >= ?", (month_start,)
        ).fetchone()[0],
        "expenses": [
            {"id": r["id"], "amount_cents": r["amount"], "category": r["category"], "note": r["note"],
             "at": r["at"], "user_name": names.get(r["user_id"], "?")}
            for r in conn.execute("SELECT * FROM money_expenses WHERE deleted = 0 ORDER BY id DESC LIMIT 15")
        ],
        "proposals": [
            {"id": p["id"], "kind": p["kind"], "value_cents": p["value"], "proposer_name": names.get(p["proposer_id"], "?"),
             "mine": p["proposer_id"] == user["id"]}
            for p in pending_proposals(conn)
        ],
    })
    return out


# ---------- akcje ----------

def _require(conn, user, role: str) -> tuple[sqlite3.Row, sqlite3.Row]:
    payer, receiver = roles(conn)
    if not payer or not receiver:
        raise ApiError(400, "Admin jeszcze nie ustawił, kto przelewa, a kto trzyma hajs")
    if role == "payer" and user["id"] != payer["id"]:
        raise ApiError(403, f"To {payer['name']} przelewa składkę")
    if role == "receiver" and user["id"] != receiver["id"]:
        raise ApiError(403, f"Hajs trzyma {receiver['name']}")
    return payer, receiver


def set_payer(conn, bus: Bus, payer_id: int) -> None:
    if not conn.execute("SELECT 1 FROM users WHERE id = ?", (payer_id,)).fetchone():
        raise ApiError(404, "Nie ma takiej osoby")
    h = core.household(conn)
    start = h["money_start_period"] or add_months(period_of(_money_now().date()), 1)
    conn.execute("UPDATE household SET money_payer_id = ?, money_start_period = ? WHERE id = 1", (payer_id, start))
    bus.state_changed()


def send_transfer(conn, bus: Bus, user) -> dict:
    payer, receiver = _require(conn, user, "payer")
    h = core.household(conn)
    if h["money_amount"] <= 0:
        raise ApiError(400, "Najpierw ustalcie wysokość składki")
    period = open_period(conn, _money_now().date())
    if transfer_for(conn, period):
        raise ApiError(409, "Ten przelew już czeka na potwierdzenie")
    cur = conn.execute(
        "INSERT INTO money_transfers (period, amount, sent_at) VALUES (?, ?, ?)",
        (period, h["money_amount"], _money_now().isoformat()),
    )
    bus.notify(receiver["id"], "money_sent", "💶 Hajs leci! Sprawdź konto",
               f"{payer['name']} mówi, że przelał(a) {fmt_eur(h['money_amount'])} (składka {period}). "
               "Zajrzyj do banku i potwierdź, że HAJS SIĘ ZGADZA.", transfer_id=cur.lastrowid)
    bus.state_changed()
    return {"transfer_id": cur.lastrowid}


def confirm_transfer(conn, bus: Bus, user, transfer_id: int) -> dict:
    payer, receiver = _require(conn, user, "receiver")
    t = conn.execute("SELECT * FROM money_transfers WHERE id = ?", (transfer_id,)).fetchone()
    if not t or t["confirmed_at"]:
        raise ApiError(404, "Ten przelew jest już potwierdzony")
    conn.execute("UPDATE money_transfers SET confirmed_at = ? WHERE id = ?", (_money_now().isoformat(), transfer_id))
    bus.notify(payer["id"], "money", "✔ HAJS SIĘ ZGADZA",
               f"{receiver['name']} potwierdza {fmt_eur(t['amount'])}. W skarbonce: {fmt_eur(balance(conn))}.")
    bus.state_changed()
    return {"balance_cents": balance(conn)}


def add_expense(conn, bus: Bus, user, amount: int, category: str, note: str) -> dict:
    payer, receiver = _require(conn, user, "receiver")
    if not 0 < amount <= 10_000_00:
        raise ApiError(400, "Podaj kwotę większą od zera")
    before = balance(conn)
    cur = conn.execute(
        "INSERT INTO money_expenses (user_id, amount, category, note, at) VALUES (?, ?, ?, ?, ?)",
        (user["id"], amount, category.strip()[:20], note.strip()[:60], _money_now().isoformat()),
    )
    after = balance(conn)
    what = " · ".join(x for x in (category.strip(), note.strip()) if x) or "zakupy"
    bus.notify(payer["id"], "money", f"💸 Przepierdolone: {fmt_eur(amount)}",
               f"{receiver['name']}: {what}. W skarbonce zostało {fmt_eur(after)}.")
    if before >= 0 > after:
        bus.notify(payer["id"], "money", "🚨 Skarbonka na minusie",
                   f"Jesteśmy {fmt_eur(after)} pod kreską. {receiver['name']} dokłada z własnej kieszeni.")
    bus.state_changed()
    return {"id": cur.lastrowid, "balance_cents": after}


def delete_expense(conn, bus: Bus, user, expense_id: int) -> dict:
    payer, receiver = _require(conn, user, "receiver")
    e = conn.execute("SELECT * FROM money_expenses WHERE id = ? AND deleted = 0", (expense_id,)).fetchone()
    if not e:
        raise ApiError(404, "Nie ma takiego wydatku")
    conn.execute("UPDATE money_expenses SET deleted = 1 WHERE id = ?", (expense_id,))
    bus.notify(payer["id"], "money", "Wydatek skasowany",
               f"{receiver['name']} usunął(-ęła) {fmt_eur(e['amount'])} ({e['category'] or 'zakupy'}). "
               f"W skarbonce: {fmt_eur(balance(conn))}.")
    bus.state_changed()
    return {"balance_cents": balance(conn)}


def set_categories(conn, bus: Bus, user, names: list[str]) -> list[str]:
    _require(conn, user, "receiver")
    clean = [n.strip()[:14] for n in names if n.strip()][:MAX_CATEGORIES]
    conn.execute("UPDATE household SET money_categories = ? WHERE id = 1", (json.dumps(clean, ensure_ascii=False),))
    bus.state_changed()
    return clean


def propose(conn, bus: Bus, user, kind: str, value: int) -> dict:
    """Składkę może zaproponować każdy (druga osoba potwierdza). Saldo poprawia trzymający hajs, potwierdza przelewający."""
    payer, receiver = _require(conn, user, "any")
    if kind not in ("amount", "balance"):
        raise ApiError(400, "Nieznany rodzaj zmiany")
    if kind == "balance" and user["id"] != receiver["id"]:
        raise ApiError(403, f"Saldo poprawia {receiver['name']}")
    if kind == "amount" and not 0 < value <= 10_000_00:
        raise ApiError(400, "Składka musi być większa od zera")
    if kind == "balance" and abs(value) > 100_000_00:
        raise ApiError(400, "Za duża kwota")
    t = _money_now().isoformat()
    conn.execute(
        "UPDATE money_proposals SET status = 'replaced', decided_at = ? WHERE status = 'pending' AND kind = ?", (t, kind)
    )
    cur = conn.execute(
        "INSERT INTO money_proposals (kind, value, proposer_id, created_at) VALUES (?, ?, ?, ?)",
        (kind, value, user["id"], t),
    )
    other = receiver if user["id"] == payer["id"] else payer
    if kind == "amount":
        title = f"Nowa składka: {fmt_eur(value)}?"
        body = f"{user['name']} chce zmienić składkę z {fmt_eur(core.household(conn)['money_amount'])} na {fmt_eur(value)}."
    else:
        title = f"Korekta skarbonki: {fmt_eur(value)}?"
        body = f"{user['name']} mówi, że w skarbonce jest naprawdę {fmt_eur(value)} (w aplikacji {fmt_eur(balance(conn))})."
    bus.notify(other["id"], "money_proposal", title, body + " Potwierdź albo odrzuć.", proposal_id=cur.lastrowid)
    bus.state_changed()
    return {"id": cur.lastrowid}


def decide(conn, bus: Bus, user, proposal_id: int, approve: bool) -> dict:
    payer, receiver = _require(conn, user, "any")
    p = conn.execute("SELECT * FROM money_proposals WHERE id = ? AND status = 'pending'", (proposal_id,)).fetchone()
    if not p:
        raise ApiError(404, "Ta propozycja jest już nieaktualna")
    if p["proposer_id"] == user["id"]:
        raise ApiError(403, "Nie zatwierdzisz sam(a) sobie, cwaniaku")
    t = _money_now().isoformat()
    conn.execute("UPDATE money_proposals SET status = ?, decided_at = ? WHERE id = ?",
                 ("approved" if approve else "rejected", t, proposal_id))
    what = "Składka" if p["kind"] == "amount" else "Korekta skarbonki"
    if approve:
        if p["kind"] == "amount":
            # Przelewy już wysłane mają zapisaną swoją kwotę, więc nowa składka działa od pierwszego nieprzelanego miesiąca.
            conn.execute("UPDATE household SET money_amount = ? WHERE id = 1", (p["value"],))
        else:
            conn.execute("INSERT INTO money_adjustments (amount, at) VALUES (?, ?)", (p["value"] - balance(conn), t))
        bus.notify(p["proposer_id"], "money", f"{what}: zgoda ✔", f"{user['name']} potwierdza {fmt_eur(p['value'])}.")
    else:
        bus.notify(p["proposer_id"], "money", f"{what}: odmowa ✖", f"{user['name']} nie zgadza się na {fmt_eur(p['value'])}.")
    bus.state_changed()
    return {"status": "approved" if approve else "rejected"}


# ---------- przypomnienia ----------

def tick(conn, bus: Bus, t: datetime | None = None) -> list[str]:
    """Raz dziennie: przelewającemu od 24., aż kliknie „Przelane”; trzymającemu, aż potwierdzi."""
    t = t or _money_now()
    sent: list[str] = []
    payer, receiver = roles(conn)
    h = core.household(conn)
    if not payer or not receiver or h["money_amount"] <= 0:
        return sent
    today = t.date()
    period = open_period(conn, today)
    if not period or due_date(period) > today:
        return sent
    tr = transfer_for(conn, period)
    today_s = today.isoformat()
    if not tr:
        if h["money_payer_reminded_on"] != today_s and schedule.can_notify(core.settings_of(payer), t, core.TZ):
            body = core.fmt(random.choice(PAYER_REMINDERS), conn, name=payer["name"], partner=receiver["name"],
                            amount=fmt_eur(h["money_amount"]), day=today.day)
            bus.notify(payer["id"], "money_due", "💶 Składka do przelania", body, ttl_min=180)
            conn.execute("UPDATE household SET money_payer_reminded_on = ? WHERE id = 1", (today_s,))
            sent.append(f"money_due:{payer['id']}")
    elif h["money_receiver_reminded_on"] != today_s and schedule.can_notify(core.settings_of(receiver), t, core.TZ):
        bus.notify(receiver["id"], "money_sent", "Potwierdź przelew",
                   f"{payer['name']} przelał(a) {fmt_eur(tr['amount'])}. Sprawdź konto i kliknij HAJS SIĘ ZGADZA.",
                   ttl_min=180, transfer_id=tr["id"])
        conn.execute("UPDATE household SET money_receiver_reminded_on = ? WHERE id = 1", (today_s,))
        sent.append(f"money_confirm:{receiver['id']}")
    return sent
