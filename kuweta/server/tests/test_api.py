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
