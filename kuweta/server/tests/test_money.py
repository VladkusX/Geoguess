import json
from datetime import date, datetime, timedelta

import pytest

from app import core, money, schedule

TZ = core.TZ


def at(y, m, d, hh, mm=0):
    return datetime(y, m, d, hh, mm, tzinfo=TZ)


@pytest.fixture
def env(tmp_path, monkeypatch):
    conn = core.connect(str(tmp_path / "k.db"))
    bus = core.Bus(conn)
    a = core.register(conn, bus, "Vlad", "ADMIN1", "admin1")
    code = core.new_pair_code(conn, None)["code"]
    b = core.register(conn, bus, "Heniuu", code, "admin1")
    free = json.dumps(schedule.normalize_settings({"cycle": ["-"], "cycle_start": "2026-11-02"}))
    conn.execute("UPDATE users SET settings=?", (free,))
    ua = conn.execute("SELECT * FROM users WHERE id=?", (a["user_id"],)).fetchone()
    ub = conn.execute("SELECT * FROM users WHERE id=?", (b["user_id"],)).fetchone()
    clock = {"t": at(2026, 11, 6, 12)}
    monkeypatch.setattr(money, "_money_now", lambda: clock["t"])
    return conn, bus, ua, ub, clock


def notes(conn, uid):
    out = []
    for r in conn.execute("SELECT payload FROM events WHERE user_id=?", (uid,)):
        p = json.loads(r["payload"])
        if p["type"] == "notify":
            out.append(p)
    return out


def test_periods():
    assert money.current_period(date(2026, 11, 6)) == "2026-11"
    assert money.current_period(date(2026, 11, 24)) == "2026-12"
    assert money.current_period(date(2026, 12, 30)) == "2027-01"
    assert money.due_date("2027-01") == date(2026, 12, 24)
    assert money.fmt_eur(12345) == "123,45 €" and money.fmt_eur(-500) == "-5 €"


def test_full_month_flow(env):
    conn, bus, ua, ub, clock = env
    assert money.state(conn, ua)["configured"] is False
    money.set_payer(conn, bus, ub["id"])  # Heniuu przelewa, Vlad trzyma hajs
    money.propose(conn, bus, ub, "amount", 300_00)
    prop = money.state(conn, ua)["proposals"][0]
    with pytest.raises(core.ApiError):
        money.decide(conn, bus, ub, prop["id"], True)  # nie sam sobie
    money.decide(conn, bus, ua, prop["id"], True)
    st = money.state(conn, ub)
    assert st["role"] == "payer" and st["period"] == "2026-12" and st["period_status"] == "not_due"

    # przed 24. cisza, od 24. przypomnienie raz dziennie
    assert money.tick(conn, bus, at(2026, 11, 20, 12)) == []
    assert money.tick(conn, bus, at(2026, 11, 24, 12)) == [f"money_due:{ub['id']}"]
    assert money.tick(conn, bus, at(2026, 11, 24, 15)) == []
    clock["t"] = at(2026, 11, 25, 10)
    tr = money.send_transfer(conn, bus, ub)
    assert any(n["kind"] == "money_sent" for n in notes(conn, ua["id"]))
    with pytest.raises(core.ApiError):
        money.confirm_transfer(conn, bus, ub, tr["transfer_id"])  # potwierdza tylko trzymający
    money.confirm_transfer(conn, bus, ua, tr["transfer_id"])
    st = money.state(conn, ua)
    assert st["balance_cents"] == 300_00 and st["period"] == "2027-01" and st["period_status"] == "not_due"

    # wydatki, zejście na minus i powiadomienia dla przelewającego
    money.add_expense(conn, bus, ua, 250_00, "Żarcie", "Biedronka")
    out = money.add_expense(conn, bus, ua, 70_00, "Żwirek", "")
    assert out["balance_cents"] == -20_00
    titles = [n["title"] for n in notes(conn, ub["id"])]
    assert any("Przepierdolone" in t for t in titles) and any("minusie" in t for t in titles)
    with pytest.raises(core.ApiError):
        money.add_expense(conn, bus, ub, 10_00, "", "")  # przelewający nie wydaje
    money.delete_expense(conn, bus, ua, out["id"])
    assert money.balance(conn) == 50_00

    # korekta salda: proponuje trzymający, potwierdza przelewający
    with pytest.raises(core.ApiError):
        money.propose(conn, bus, ub, "balance", 0)
    p = money.propose(conn, bus, ua, "balance", 42_00)
    money.decide(conn, bus, ub, p["id"], True)
    assert money.balance(conn) == 42_00


def test_amount_change_after_transfer_hits_next_month(env):
    conn, bus, ua, ub, clock = env
    money.set_payer(conn, bus, ub["id"])
    money.decide(conn, bus, ua, money.propose(conn, bus, ub, "amount", 200_00)["id"], True)
    clock["t"] = at(2026, 11, 25, 10)
    tr = money.send_transfer(conn, bus, ub)
    money.decide(conn, bus, ub, money.propose(conn, bus, ua, "amount", 250_00)["id"], True)
    money.confirm_transfer(conn, bus, ua, tr["transfer_id"])
    assert money.balance(conn) == 200_00  # grudzień poszedł po starej stawce
    assert money.state(conn, ub)["transfer_cents"] == 250_00  # styczeń już po nowej


def test_receiver_reminded_to_confirm(env):
    conn, bus, ua, ub, clock = env
    money.set_payer(conn, bus, ub["id"])
    money.decide(conn, bus, ua, money.propose(conn, bus, ub, "amount", 100_00)["id"], True)
    clock["t"] = at(2026, 11, 24, 9)
    money.send_transfer(conn, bus, ub)
    assert money.tick(conn, bus, at(2026, 11, 25, 12)) == [f"money_confirm:{ua['id']}"]
