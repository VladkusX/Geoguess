import json
from datetime import date, datetime, timedelta

import pytest

from app import core, schedule

TZ = core.TZ


def at(y, m, d, hh, mm=0):
    return datetime(y, m, d, hh, mm, tzinfo=TZ)


# 2026-11-02 to poniedziałek
FIVE = schedule.normalize_settings({"cycle": schedule.PRESET_5BRYG, "cycle_start": "2026-11-02"})
THREE = schedule.normalize_settings({"cycle": schedule.PRESET_3ZM, "cycle_start": "2026-11-02"})


def test_cycle_codes():
    codes = [schedule.shift_code(FIVE, date(2026, 11, 2) + timedelta(days=i)) for i in range(11)]
    assert codes == ["R", "R", "P", "P", "N", "N", "-", "-", "-", "-", "R"]
    week2 = [schedule.shift_code(THREE, date(2026, 11, 9) + timedelta(days=i)) for i in range(7)]
    assert week2 == ["P"] * 5 + ["-", "-"]


def test_morning_shift():
    assert schedule.classify(FIVE, at(2026, 11, 2, 5, 45), TZ) == "work"  # dojazd
    assert schedule.classify(FIVE, at(2026, 11, 2, 13, 0), TZ) == "work"
    assert schedule.classify(FIVE, at(2026, 11, 2, 14, 30), TZ) == "free"  # po rannej bez snu
    assert schedule.classify(FIVE, at(2026, 11, 2, 23, 30), TZ) == "sleep"


def test_afternoon_shift_grace_hour():
    # 4 listopada: P 14-22, godzina po robocie można męczyć mimo ciszy nocnej
    s = dict(FIVE, quiet_start="22:00")
    assert schedule.classify(s, at(2026, 11, 4, 12, 0), TZ) == "free"
    assert schedule.classify(s, at(2026, 11, 4, 13, 45), TZ) == "work"
    assert schedule.classify(s, at(2026, 11, 4, 22, 30), TZ) == "free"
    assert schedule.classify(s, at(2026, 11, 4, 23, 15), TZ) == "sleep"


def test_night_shift_sleep():
    # 6 listopada: N 22-6 -> 7 listopada 6:00-7:00 grace, potem sen do 14:00
    assert schedule.classify(FIVE, at(2026, 11, 6, 23, 0), TZ) == "work"
    assert schedule.classify(FIVE, at(2026, 11, 7, 6, 30), TZ) == "free"
    assert schedule.classify(FIVE, at(2026, 11, 7, 10, 0), TZ) == "sleep"
    assert schedule.classify(FIVE, at(2026, 11, 7, 14, 30), TZ) == "free"


def test_override_day_off():
    s = dict(FIVE, overrides={"2026-11-02": "-"})
    assert schedule.classify(s, at(2026, 11, 2, 10, 0), TZ) == "free"


def test_timeline_covers_day():
    tl = schedule.day_timeline(FIVE, date(2026, 11, 2), TZ)
    assert sum(seg["minutes"] for seg in tl) == 24 * 60


@pytest.fixture
def env(tmp_path):
    conn = core.connect(str(tmp_path / "k.db"))
    bus = core.Bus(conn)
    a = core.register(conn, bus, "Vlad", "ADMIN1", "admin1")
    code = core.new_pair_code(conn, None)["code"]
    b = core.register(conn, bus, "Ania", code, "admin1")
    ua = conn.execute("SELECT * FROM users WHERE id=?", (a["user_id"],)).fetchone()
    ub = conn.execute("SELECT * FROM users WHERE id=?", (b["user_id"],)).fetchone()
    # oba grafiki wolne, żeby testować samą logikę dyżuru
    free = json.dumps(schedule.normalize_settings({"cycle": ["-"], "cycle_start": "2026-11-02"}))
    conn.execute("UPDATE users SET settings=?", (free,))
    return conn, bus, ua, ub


def notes(conn, uid):
    return [json.loads(r["payload"]) for r in conn.execute("SELECT payload FROM events WHERE user_id=?", (uid,))
            if json.loads(r["payload"])["type"] == "notify"]


def test_pairing_sets_first_duty(env):
    conn, bus, ua, ub = env
    h = core.household(conn)
    assert h["assignee_id"] == ua["id"]
    with pytest.raises(core.ApiError):
        core.register(conn, bus, "Trzeci", "ZLYKOD", "admin1")


def test_reminders_every_interval(env):
    conn, bus, ua, ub = env
    conn.execute("UPDATE household SET due_date='2026-11-02'")
    assert core.tick(conn, bus, at(2026, 11, 1, 12)) == []  # przed terminem cisza
    assert core.tick(conn, bus, at(2026, 11, 2, 9, 0)) == ["reminder:1:1"]
    assert core.tick(conn, bus, at(2026, 11, 2, 9, 10)) == []
    assert core.tick(conn, bus, at(2026, 11, 2, 9, 30)) == ["reminder:1:2"]
    assert core.tick(conn, bus, at(2026, 11, 2, 10, 0)) == ["reminder:1:3"]
    assert core.tick(conn, bus, at(2026, 11, 2, 10, 30)) == ["reminder:1:4"]
    assert core.tick(conn, bus, at(2026, 11, 2, 11, 0)) == ["reminder:1:4"]
    assert core.tick(conn, bus, at(2026, 11, 2, 23, 30)) == []  # cisza nocna
    # dzień po terminie: ostrzej + donos do drugiej osoby
    sent = core.tick(conn, bus, at(2026, 11, 3, 9, 0))
    assert "reminder:1:3" in sent and "partner:2" in sent


def test_clean_rotates_and_notifies(env):
    conn, bus, ua, ub = env
    conn.execute("UPDATE household SET due_date='2026-11-02'")
    res = core.clean(conn, bus, ua)
    assert res["next_assignee_id"] == ub["id"]
    assert any(n["kind"] == "cleaned" for n in notes(conn, ub["id"]))
    assert core.streak(conn, ua["id"]) == 1


def test_extension_flow(env):
    conn, bus, ua, ub = env
    today = core.now().date()
    conn.execute("UPDATE household SET due_date=?", (today.isoformat(),))
    ext = core.request_extension(conn, bus, ua)
    assert any(n["kind"] == "ext_request" for n in notes(conn, ub["id"]))
    with pytest.raises(core.ApiError):
        core.decide_extension(conn, bus, ua, ext["id"], True)  # nie sam sobie
    core.decide_extension(conn, bus, ub, ext["id"], True)
    h = core.household(conn)
    assert h["due_date"] == (today + timedelta(days=1)).isoformat()
    assert h["extended"] == 1
    core.clean(conn, bus, ua)
    assert core.streak(conn, ua["id"]) == 0  # przedłużenie przerywa streak


def test_state_shape(env):
    conn, bus, ua, ub = env
    st = core.state(conn, ua)
    assert st["duty"]["is_me"] and st["partner"]["name"] == "Ania"
    assert st["duty"]["next_name"] == "Ania"
    assert len(st["upcoming"]) == 14


def test_cat_names_in_messages(env):
    conn, bus, ua, ub = env
    assert core.set_cats(conn, [" Beżi ", "Mruczek", ""]) == ["Beżi", "Mruczek"]
    assert core.fmt("{cat} patrzy", conn) in ("Beżi patrzy", "Mruczek patrzy")
    assert core.fmt("{cats} czekają", conn) == "Beżi i Mruczek czekają"
    core.set_cats(conn, [])
    assert core.fmt("{cats}/{cat}", conn) == "Koty/Kot"
    assert conn.execute("SELECT COUNT(*) FROM messages WHERE text LIKE '%{cat}%'").fetchone()[0] > 0
    assert core.state(conn, ua)["cats"] == []


def test_migration_is_idempotent(tmp_path):
    path = str(tmp_path / "m.db")
    n1 = core.connect(path).execute("SELECT COUNT(*) FROM messages").fetchone()[0]
    n2 = core.connect(path).execute("SELECT COUNT(*) FROM messages").fetchone()[0]
    assert n1 == n2 > 0


def test_reseed_keeps_custom_messages(tmp_path):
    path = str(tmp_path / "r.db")
    conn = core.connect(path)
    conn.execute("INSERT INTO messages (category, level, text) VALUES ('reminder', 2, 'mój tekst')")
    conn.execute("UPDATE messages SET text = 'stary' WHERE builtin = 1")
    conn.execute("UPDATE household SET seed_version = 2")
    conn = core.connect(path)
    texts = [r[0] for r in conn.execute("SELECT text FROM messages")]
    assert "mój tekst" in texts and "stary" not in texts
    assert any("kurwa" in t.lower() for t in texts)


def test_messages_do_not_repeat_quickly(env):
    conn, bus, ua, ub = env
    level2 = conn.execute("SELECT COUNT(*) FROM messages WHERE category='reminder' AND level IN (1, 2)").fetchone()[0]
    picked = [core.pick_message(conn, "reminder", 2) for _ in range(level2 // 2)]
    assert len(set(picked)) == len(picked)  # pierwsza połowa puli bez żadnego powtórzenia


def test_ile_dni_and_placeholders(env):
    conn, bus, ua, ub = env
    assert core.fmt("{ile_dni}", conn, days=1) == "1 dzień"
    assert core.fmt("{ile_dni}", conn, days=3) == "3 dni"
    core.set_cats(conn, ["Beżi"])
    for (text,) in conn.execute("SELECT text FROM messages"):
        out = core.fmt(text, conn, name="Vlad", partner="Ania", days=2)
        assert "{" not in out, text
    assert conn.execute("SELECT COUNT(*) FROM messages").fetchone()[0] >= 140


def test_pool_mentions_cats_and_drops_old_texts(tmp_path):
    path = str(tmp_path / "v.db")
    conn = core.connect(path)
    conn.execute("INSERT INTO messages (category, level, text, builtin) VALUES ('reminder', 1, 'Ej, {name}. Kuweta. Wiesz co robić.', 1)")
    conn.execute("UPDATE household SET seed_version = 4")
    conn = core.connect(path)
    texts = [r[0] for r in conn.execute("SELECT text FROM messages")]
    assert "Ej, {name}. Kuweta. Wiesz co robić." not in texts
    with_cats = [t for t in texts if "{cat}" in t or "{cats}" in t]
    assert len(with_cats) / len(texts) > 0.75


def test_no_overdue_texts_on_due_day(env):
    conn, bus, ua, ub = env
    for _ in range(60):
        text = core.pick_message(conn, "reminder", 4, overdue=False)
        assert "{ile_dni}" not in text and "{days}" not in text
