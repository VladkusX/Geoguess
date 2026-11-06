import importlib
import os

from fastapi.testclient import TestClient


def test_admin_flow(tmp_path):
    os.environ["KUWETA_DB"] = str(tmp_path / "api.db")
    os.environ["KUWETA_ADMIN_CODE"] = "tajne"
    main = importlib.reload(importlib.import_module("app.main"))
    c = TestClient(main.app)
    ta = c.post("/api/register", json={"name": "Vlad", "code": "tajne"}).json()["token"]
    ha = {"Authorization": f"Bearer {ta}"}
    code = c.post("/api/admin/pair-code", headers=ha).json()["code"]
    tb = c.post("/api/register", json={"name": "Ania", "code": code}).json()["token"]
    hb = {"Authorization": f"Bearer {tb}"}

    r = c.put("/api/admin/settings", headers=ha, json={"interval_days": 3, "max_level": 2, "notify_interval": {"2": 15}})
    assert r.status_code == 200
    users = {u["name"]: u for u in r.json()["users"]}
    assert users["Ania"]["notify_interval_min"] == 15 and r.json()["interval_days"] == 3

    assert c.get("/api/admin", headers=hb).status_code == 403
    r = c.post("/api/admin/messages", headers=ha, json={"category": "reminder", "level": 2, "text": "Własny tekst {name}"})
    assert any(m["text"].startswith("Własny") for m in r.json()["messages"])

    sched = c.get("/api/schedule", headers=hb).json()
    sched["overrides"] = {"2026-12-24": "-"}
    assert c.put("/api/schedule", headers=hb, json=sched).status_code == 200
    assert c.put("/api/schedule", headers=hb, json={"cycle": ["X"]}).status_code == 400
    assert c.get("/api/state", headers={"Authorization": "Bearer zly"}).status_code == 401
    assert c.get("/api/ranking", headers=hb).status_code == 200
    r = c.put("/api/admin/cats", headers=ha, json={"cats": ["Beżi", "Mruczek"]})
    assert r.json()["cats"] == ["Beżi", "Mruczek"]
    assert c.get("/api/state", headers=hb).json()["cats"] == ["Beżi", "Mruczek"]

    r = c.post("/api/admin/sounds?name=beżi.mp3", headers={**ha, "content-type": "audio/mpeg"}, content=b"ID3fake")
    assert r.status_code == 200
    sid = r.json()["sounds"][0]["id"]
    assert c.post("/api/admin/sounds?name=x.exe", headers={**ha, "content-type": "application/x-msdownload"}, content=b"x").status_code == 415
    assert c.post("/api/admin/sounds", headers={**hb, "content-type": "audio/mpeg"}, content=b"x").status_code == 403
    c.put("/api/admin/sound-slots/reminder", headers=ha, json={"sound_id": sid})
    st = c.get("/api/state", headers=hb).json()
    assert st["sounds"]["reminder"]["name"] == "beżi" and st["sounds"]["partner"] is None
    assert c.get(f"/api/sounds/{sid}", headers=hb).content == b"ID3fake"
    c.delete(f"/api/admin/sounds/{sid}", headers=ha)
    assert c.get("/api/state", headers=hb).json()["sounds"]["reminder"] is None


def test_missed_events_catch_up(tmp_path):
    os.environ["KUWETA_DB"] = str(tmp_path / "api.db")
    os.environ["KUWETA_ADMIN_CODE"] = "tajne"
    main = importlib.reload(importlib.import_module("app.main"))
    c = TestClient(main.app)
    ta = c.post("/api/register", json={"name": "Vlad", "code": "tajne"}).json()["token"]
    ha = {"Authorization": f"Bearer {ta}"}
    code = c.post("/api/admin/pair-code", headers=ha).json()["code"]
    tb = c.post("/api/register", json={"name": "Ania", "code": code}).json()["token"]
    hb = {"Authorization": f"Bearer {tb}"}

    start = c.get("/api/events/missed?after=0", headers=ha).json()["last_id"]
    assert start > 0
    assert c.post("/api/clean", headers=hb).status_code == 200  # Ania sprząta, Vlad ma potwierdzić
    r = c.get(f"/api/events/missed?after={start}", headers=ha).json()
    kinds = [e["payload"].get("kind") for e in r["events"]]
    assert "review" in kinds and r["last_id"] > start
    cid = c.get("/api/state", headers=ha).json()["pending_review"]["id"]
    assert c.post(f"/api/cleanings/{cid}/review", headers=hb, json={"approve": True}).status_code == 403
    assert c.post(f"/api/cleanings/{cid}/review", headers=ha, json={"approve": True}).json()["status"] == "approved"


def test_money_endpoints(tmp_path):
    os.environ["KUWETA_DB"] = str(tmp_path / "api.db")
    os.environ["KUWETA_ADMIN_CODE"] = "tajne"
    main = importlib.reload(importlib.import_module("app.main"))
    c = TestClient(main.app)
    ta = c.post("/api/register", json={"name": "Vlad", "code": "tajne"}).json()["token"]
    ha = {"Authorization": f"Bearer {ta}"}
    code = c.post("/api/admin/pair-code", headers=ha).json()["code"]
    r = c.post("/api/register", json={"name": "Heniuu", "code": code}).json()
    hb = {"Authorization": f"Bearer {r['token']}"}

    assert c.get("/api/state", headers=ha).json()["money"]["configured"] is False
    assert c.put("/api/admin/money", headers=hb, json={"payer_id": r["user_id"]}).status_code == 403
    assert c.put("/api/admin/money", headers=ha, json={"payer_id": r["user_id"]}).json()["role"] == "receiver"
    pid = c.post("/api/money/proposals", headers=hb, json={"kind": "amount", "value_cents": 30000}).json()["id"]
    assert c.post(f"/api/money/proposals/{pid}/decide", headers=ha, json={"approve": True}).status_code == 200
    assert c.post("/api/money/transfer", headers=ha).status_code == 403
    tid = c.post("/api/money/transfer", headers=hb).json()["transfer_id"]
    assert c.post(f"/api/money/transfer/{tid}/confirm", headers=ha).json()["balance_cents"] == 30000
    assert c.post("/api/money/expenses", headers=ha, json={"amount_cents": 1500, "category": "Żwirek"}).status_code == 200
    m = c.get("/api/state", headers=hb).json()["money"]
    assert m["balance_cents"] == 28500 and m["expenses"][0]["category"] == "Żwirek"
    assert c.put("/api/money/categories", headers=ha, json={"categories": ["Żarcie", "Weterynarz"]}).json()["categories"] == ["Żarcie", "Weterynarz"]
