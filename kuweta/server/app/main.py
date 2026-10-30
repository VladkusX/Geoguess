"""HTTP API dla aplikacji Kuweta Patrol."""
from __future__ import annotations

import asyncio
import json
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime

from fastapi import Depends, FastAPI, Header, Request
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from . import core, schedule

log = logging.getLogger("kuweta")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

DB_PATH = os.environ.get("KUWETA_DB", "/data/kuweta.db")
ADMIN_CODE = os.environ.get("KUWETA_ADMIN_CODE", "")

conn = core.connect(DB_PATH)
SOUNDS_DIR = core.sounds_dir(DB_PATH)
bus = core.Bus(conn)
wakeups: dict[int, set[asyncio.Event]] = {}


def _wake(user_id: int) -> None:
    for ev in wakeups.get(user_id, ()):
        ev.set()


bus.listeners.append(_wake)


async def scheduler() -> None:
    while True:
        try:
            for item in core.tick(conn, bus):
                log.info("wysłano %s", item)
            bus.cleanup()
        except Exception:
            log.exception("błąd harmonogramu")
        await asyncio.sleep(60 - datetime.now().second)


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not ADMIN_CODE:
        log.warning("Brak KUWETA_ADMIN_CODE - admin nie będzie mógł się zarejestrować")
    task = asyncio.create_task(scheduler())
    yield
    task.cancel()


app = FastAPI(title="Kuweta Patrol", lifespan=lifespan)


@app.exception_handler(core.ApiError)
async def api_error(_: Request, exc: core.ApiError):
    return JSONResponse(status_code=exc.status, content={"error": exc.message})


@app.exception_handler(ValueError)
async def value_error(_: Request, exc: ValueError):
    return JSONResponse(status_code=400, content={"error": str(exc)})


def current_user(authorization: str = Header(default="")):
    token = authorization.removeprefix("Bearer ").strip()
    return core.user_by_token(conn, token)


def admin_user(user=Depends(current_user)):
    if not user["is_admin"]:
        raise core.ApiError(403, "Tylko dla admina")
    return user


# ---------- modele ----------

class RegisterIn(BaseModel):
    name: str
    code: str


class DecideIn(BaseModel):
    approve: bool


class ScheduleIn(BaseModel):
    cycle: list[str]
    cycle_start: str | None = None
    shifts: dict[str, list[str]] = Field(default_factory=dict)
    commute_min: int = 30
    grace_min: int = 60
    sleep_after_night_h: int = 7
    quiet_start: str = "23:00"
    quiet_end: str = "08:00"
    overrides: dict[str, str] = Field(default_factory=dict)


class AdminSettingsIn(BaseModel):
    interval_days: int | None = None
    max_level: int | None = None
    notify_interval: dict[int, int] = Field(default_factory=dict)


class AdminStateIn(BaseModel):
    assignee_id: int
    due_date: str


class CatsIn(BaseModel):
    cats: list[str]


class SoundSlotIn(BaseModel):
    sound_id: int | None = None


class MessageIn(BaseModel):
    category: str
    level: int = 0
    text: str


# ---------- endpointy ----------

@app.get("/api/health")
async def health():
    return {"ok": True, "time": core.now().isoformat()}


@app.post("/api/register")
async def register(body: RegisterIn):
    return core.register(conn, bus, body.name, body.code, ADMIN_CODE)


@app.get("/api/state")
async def get_state(user=Depends(current_user)):
    return core.state(conn, user)


@app.post("/api/clean")
async def clean(user=Depends(current_user)):
    return core.clean(conn, bus, user)


@app.post("/api/extensions")
async def request_extension(user=Depends(current_user)):
    return core.request_extension(conn, bus, user)


@app.post("/api/extensions/{ext_id}/decide")
async def decide_extension(ext_id: int, body: DecideIn, user=Depends(current_user)):
    return core.decide_extension(conn, bus, user, ext_id, body.approve)


@app.post("/api/test-notify")
async def test_notify(user=Depends(current_user)):
    body = core.fmt(core.pick_message(conn, "reminder", 2), conn, name=user["name"], partner="", days=0)
    bus.notify(user["id"], "test", "Test powiadomień", body, ttl_min=10)
    return {"ok": True}


@app.get("/api/schedule")
async def get_schedule(user=Depends(current_user)):
    return core.settings_of(user)


def _save_schedule(user_id: int, body: ScheduleIn) -> dict:
    s = schedule.normalize_settings(body.model_dump())
    schedule.validate_settings(s)
    conn.execute("UPDATE users SET settings = ? WHERE id = ?", (json.dumps(s), user_id))
    bus.state_changed()
    return s


@app.put("/api/schedule")
async def put_schedule(body: ScheduleIn, user=Depends(current_user)):
    return _save_schedule(user["id"], body)


@app.get("/api/ranking")
async def get_ranking(month: str | None = None, user=Depends(current_user)):
    return core.ranking(conn, month or core.now().strftime("%Y-%m"))


@app.get("/api/presets")
async def presets():
    return {
        "5bryg": schedule.PRESET_5BRYG,
        "3zm": schedule.PRESET_3ZM,
        "shifts": schedule.DEFAULT_SHIFTS,
    }


# ---------- admin ----------

@app.get("/api/admin")
async def admin_overview(user=Depends(admin_user)):
    h = core.household(conn)
    return {
        "interval_days": h["interval_days"],
        "max_level": h["max_level"],
        "assignee_id": h["assignee_id"],
        "due_date": h["due_date"],
        "cats": core.cats(conn),
        "sounds": core.list_sounds(conn),
        "sound_slots": core.sound_info(conn),
        "users": [
            {**core.user_public(u), "schedule": core.settings_of(u)} for u in core.users(conn)
        ],
        "messages": [dict(r) for r in conn.execute("SELECT * FROM messages ORDER BY category, level, id")],
    }


@app.put("/api/admin/settings")
async def admin_settings(body: AdminSettingsIn, user=Depends(admin_user)):
    if body.interval_days is not None:
        if not 1 <= body.interval_days <= 14:
            raise core.ApiError(400, "Co ile dni: od 1 do 14")
        conn.execute("UPDATE household SET interval_days = ? WHERE id = 1", (body.interval_days,))
    if body.max_level is not None:
        if not 1 <= body.max_level <= 4:
            raise core.ApiError(400, "Poziom wulgarności: od 1 do 4")
        conn.execute("UPDATE household SET max_level = ? WHERE id = 1", (body.max_level,))
    for uid, minutes in body.notify_interval.items():
        if not 5 <= minutes <= 240:
            raise core.ApiError(400, "Częstotliwość: od 5 do 240 minut")
        conn.execute("UPDATE users SET notify_interval_min = ? WHERE id = ?", (minutes, uid))
    bus.state_changed()
    return await admin_overview(user)


@app.put("/api/admin/cats")
async def admin_cats(body: CatsIn, user=Depends(admin_user)):
    core.set_cats(conn, body.cats)
    bus.state_changed()
    return await admin_overview(user)


@app.get("/api/sounds/{sound_id}")
async def get_sound(sound_id: int, user=Depends(current_user)):
    row = core.sound_row(conn, sound_id)
    return FileResponse(os.path.join(SOUNDS_DIR, f"{row['id']}.{row['ext']}"), media_type=row["mime"])


@app.post("/api/admin/sounds")
async def admin_upload_sound(request: Request, name: str = "dźwięk", user=Depends(admin_user)):
    data = await request.body()
    core.add_sound(conn, SOUNDS_DIR, name, request.headers.get("content-type", ""), data)
    return await admin_overview(user)


@app.delete("/api/admin/sounds/{sound_id}")
async def admin_delete_sound(sound_id: int, user=Depends(admin_user)):
    core.delete_sound(conn, SOUNDS_DIR, sound_id)
    bus.state_changed()
    return await admin_overview(user)


@app.put("/api/admin/sound-slots/{slot}")
async def admin_sound_slot(slot: str, body: SoundSlotIn, user=Depends(admin_user)):
    core.assign_sound(conn, slot, body.sound_id)
    bus.state_changed()
    return await admin_overview(user)


@app.put("/api/admin/state")
async def admin_state(body: AdminStateIn, user=Depends(admin_user)):
    datetime.fromisoformat(body.due_date)
    if not conn.execute("SELECT 1 FROM users WHERE id = ?", (body.assignee_id,)).fetchone():
        raise core.ApiError(404, "Nie ma takiej osoby")
    conn.execute(
        "UPDATE household SET assignee_id = ?, due_date = ?, extended = 0, partner_alerted_on = NULL, "
        "last_reminder_at = NULL WHERE id = 1",
        (body.assignee_id, body.due_date),
    )
    bus.state_changed()
    return await admin_overview(user)


@app.put("/api/admin/users/{user_id}/schedule")
async def admin_user_schedule(user_id: int, body: ScheduleIn, user=Depends(admin_user)):
    return _save_schedule(user_id, body)


@app.post("/api/admin/pair-code")
async def admin_pair_code(user_id: int | None = None, user=Depends(admin_user)):
    return core.new_pair_code(conn, user_id)


@app.post("/api/admin/messages")
async def admin_add_message(body: MessageIn, user=Depends(admin_user)):
    if body.category not in core.CATEGORIES:
        raise core.ApiError(400, "Nieznana kategoria")
    text = body.text.strip()
    if not 3 <= len(text) <= 300:
        raise core.ApiError(400, "Tekst: od 3 do 300 znaków")
    conn.execute(
        "INSERT INTO messages (category, level, text) VALUES (?, ?, ?)",
        (body.category, max(0, min(body.level, 4)), text),
    )
    return await admin_overview(user)


@app.delete("/api/admin/messages/{msg_id}")
async def admin_delete_message(msg_id: int, user=Depends(admin_user)):
    conn.execute("DELETE FROM messages WHERE id = ?", (msg_id,))
    return await admin_overview(user)


# ---------- push (Server-Sent Events) ----------

@app.get("/api/events/missed")
async def missed_events(after: int, user=Depends(current_user)):
    """Zapasowa droga dla watchdoga: zdarzenia, które mogły przepaść, gdy SSE po cichu padło."""
    if after <= 0:
        return {"events": [], "last_id": bus.last_id(user["id"])}
    rows = bus.pending(user["id"], after)
    return {"events": [{"id": r["id"], "payload": json.loads(r["payload"])} for r in rows],
            "last_id": rows[-1]["id"] if rows else after}


@app.get("/api/events")
async def events(
    request: Request,
    user=Depends(current_user),
    last_event_id: str | None = Header(default=None),
    after: int | None = None,
):
    uid = user["id"]
    cursor = after if after is not None else int(last_event_id or 0)
    if cursor == 0:
        cursor = bus.last_id(uid)  # nowe urządzenie: bez zaległości

    async def stream():
        nonlocal cursor
        wake = asyncio.Event()
        wakeups.setdefault(uid, set()).add(wake)
        try:
            yield f"retry: 5000\nevent: hello\ndata: {json.dumps({'last_id': cursor})}\n\n"
            while not await request.is_disconnected():
                wake.clear()
                for row in bus.pending(uid, cursor):
                    cursor = row["id"]
                    yield f"id: {row['id']}\ndata: {row['payload']}\n\n"
                try:
                    await asyncio.wait_for(wake.wait(), timeout=25)
                except asyncio.TimeoutError:
                    yield ": ping\n\n"
        finally:
            wakeups[uid].discard(wake)

    headers = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
    return StreamingResponse(stream(), media_type="text/event-stream", headers=headers)
