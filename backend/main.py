import asyncio
import json
import os
import secrets
import smtplib
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage
from pathlib import Path
from typing import Generator, Optional

from dotenv import load_dotenv
from fastapi import BackgroundTasks, Depends, FastAPI, File, Header, HTTPException, Query, Request, Response, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, EmailStr, Field
from passlib.context import CryptContext
from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, relationship, sessionmaker


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
ALERT_IMAGES_DIR = DATA_DIR / "alert-images"
AUTHORIZED_FACES_DIR = DATA_DIR / "authorized-faces"

DATA_DIR.mkdir(exist_ok=True)
ALERT_IMAGES_DIR.mkdir(exist_ok=True)
AUTHORIZED_FACES_DIR.mkdir(exist_ok=True)

load_dotenv(BASE_DIR / ".env")

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./data/intruder-alert.db")
DETECTOR_API_KEY = os.getenv("DETECTOR_API_KEY", "change-me-detector-key")
TOKEN_VALIDITY_HOURS = int(os.getenv("TOKEN_VALIDITY_HOURS", "168"))
CORS_ORIGINS = [origin.strip() for origin in os.getenv("CORS_ORIGINS", "http://localhost:5173").split(",") if origin.strip()]

EMAIL_ENABLED_DEFAULT = os.getenv("EMAIL_ENABLED", "false").lower() == "true"
EMAIL_FROM = os.getenv("EMAIL_FROM", "")
EMAIL_DASHBOARD_BASE_URL_DEFAULT = os.getenv("EMAIL_DASHBOARD_BASE_URL", "http://localhost:5173")
SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USERNAME = os.getenv("SMTP_USERNAME", "")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")

connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, future=True, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
password_context = CryptContext(schemes=["pbkdf2_sha256"], deprecated="auto")


class Base(DeclarativeBase):
    pass


class UserAccount(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    full_name: Mapped[str] = mapped_column(String(255))
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))


class UserSession(Base):
    __tablename__ = "user_sessions"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    token: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    device_name: Mapped[str] = mapped_column(String(255))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

    user: Mapped[UserAccount] = relationship()


class Alert(Base):
    __tablename__ = "alerts"

    id: Mapped[int] = mapped_column(primary_key=True)
    camera_id: Mapped[str] = mapped_column(String(255))
    timestamp: Mapped[str] = mapped_column(String(255))
    image_path: Mapped[str] = mapped_column(String(500))
    file_name: Mapped[str] = mapped_column(String(255))
    message: Mapped[str] = mapped_column(Text)
    severity: Mapped[str] = mapped_column(String(32))
    status: Mapped[str] = mapped_column(String(32))


class AuthorizedFace(Base):
    __tablename__ = "authorized_faces"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    display_name: Mapped[str] = mapped_column(String(255))
    image_path: Mapped[str] = mapped_column(String(500))
    updated_at: Mapped[str] = mapped_column(String(255))

    user: Mapped[UserAccount] = relationship()


class DetectorStatus(Base):
    __tablename__ = "detector_status"

    id: Mapped[int] = mapped_column(primary_key=True)
    camera_id: Mapped[str] = mapped_column(String(255))
    last_heartbeat_at: Mapped[str] = mapped_column(String(255))
    alert_cooldown_seconds: Mapped[int] = mapped_column(Integer)


class SystemSettings(Base):
    __tablename__ = "system_settings"

    id: Mapped[int] = mapped_column(primary_key=True)
    email_enabled: Mapped[bool] = mapped_column(Boolean, default=EMAIL_ENABLED_DEFAULT)
    dashboard_base_url: Mapped[str] = mapped_column(String(500), default=EMAIL_DASHBOARD_BASE_URL_DEFAULT)


class SignupRequest(BaseModel):
    fullName: str = Field(min_length=1)
    email: EmailStr
    password: str = Field(min_length=8, max_length=72)
    deviceName: str = Field(min_length=1)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str
    deviceName: str = Field(min_length=1)


class AlertRequest(BaseModel):
    cameraId: str
    timestamp: str
    imageBase64: str
    fileName: str
    message: str


class AlertStatusUpdateRequest(BaseModel):
    status: str


class SystemSettingsUpdateRequest(BaseModel):
    emailEnabled: bool
    dashboardBaseUrl: str = Field(min_length=1)


class DetectorHeartbeatRequest(BaseModel):
    cameraId: str
    timestamp: str
    alertCooldownSeconds: int


class EventHub:
    def __init__(self) -> None:
        self.queues: list[asyncio.Queue] = []

    async def subscribe(self) -> asyncio.Queue:
        queue: asyncio.Queue = asyncio.Queue()
        self.queues.append(queue)
        await queue.put(("connected", None))
        return queue

    def unsubscribe(self, queue: asyncio.Queue) -> None:
        if queue in self.queues:
            self.queues.remove(queue)

    async def publish(self, event_type: str, alert: Optional[dict]) -> None:
        payload = {"type": event_type, "alert": alert}
        for queue in list(self.queues):
            await queue.put(("alert", payload))


event_hub = EventHub()


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def normalize_datetime(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def normalize_email(email: str) -> str:
    return email.strip().lower()


def slugify_name(value: str) -> str:
    return "".join(char.lower() if char.isalnum() else "_" for char in value.strip()).strip("_") or "authorized_user"


def read_file_bytes(path: str) -> bytes:
    return Path(path).read_bytes()


def store_bytes(directory: Path, filename: str, content: bytes) -> str:
    directory.mkdir(exist_ok=True)
    file_path = directory / filename
    file_path.write_bytes(content)
    return str(file_path)


def get_or_create_settings(db: Session) -> SystemSettings:
    settings = db.get(SystemSettings, 1)
    if settings is None:
        settings = SystemSettings(id=1, email_enabled=EMAIL_ENABLED_DEFAULT, dashboard_base_url=EMAIL_DASHBOARD_BASE_URL_DEFAULT)
        db.add(settings)
        db.commit()
        db.refresh(settings)
    return settings


def serialize_user_profile(user: UserAccount, session: UserSession) -> dict:
    return {
        "id": user.id,
        "fullName": user.full_name,
        "email": user.email,
        "activeDeviceName": session.device_name,
    }


def serialize_alert(alert: Alert) -> dict:
    return {
        "id": alert.id,
        "cameraId": alert.camera_id,
        "timestamp": alert.timestamp,
        "imageBase64": None,
        "fileName": alert.file_name,
        "message": alert.message,
        "severity": alert.severity,
        "status": alert.status,
    }


def serialize_face(face: Optional[AuthorizedFace]) -> dict:
    if face is None:
        return {"enrolled": False, "displayName": "", "updatedAt": ""}
    return {
        "id": face.id,
        "displayName": face.display_name,
        "updatedAt": face.updated_at,
        "enrolled": True,
    }


def serialize_settings(db: Session) -> dict:
    settings = get_or_create_settings(db)
    detector_status = db.scalar(select(DetectorStatus).order_by(DetectorStatus.id.desc()).limit(1))
    detector_online = False
    if detector_status:
        try:
            detector_online = (utc_now() - datetime.fromisoformat(detector_status.last_heartbeat_at.replace("Z", "+00:00"))).total_seconds() <= 45
        except Exception:
            detector_online = False
    return {
        "emailEnabled": settings.email_enabled,
        "dashboardBaseUrl": settings.dashboard_base_url,
        "fromEmail": EMAIL_FROM,
        "registeredRecipients": db.query(UserAccount).count(),
        "detectorOnline": detector_online,
        "detectorCameraId": detector_status.camera_id if detector_status else "",
        "lastHeartbeatAt": detector_status.last_heartbeat_at if detector_status else "",
        "detectorAlertCooldownSeconds": detector_status.alert_cooldown_seconds if detector_status else None,
    }


def get_token_from_request(request: Request) -> Optional[str]:
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        return auth_header[7:].strip()
    return request.query_params.get("access_token")


def get_current_session(request: Request, db: Session = Depends(get_db)) -> UserSession:
    token = get_token_from_request(request)
    if not token:
        raise HTTPException(status_code=401, detail="Authentication required.")

    session = db.scalar(select(UserSession).where(UserSession.token == token))
    if session is None or normalize_datetime(session.expires_at) < utc_now():
        raise HTTPException(status_code=401, detail="Authentication required.")
    return session


def require_detector_key(detector_key: Optional[str]) -> None:
    if detector_key != DETECTOR_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid detector key.")


def send_alert_email(alert: Alert, recipients: list[str], dashboard_base_url: str) -> None:
    if not EMAIL_FROM or not SMTP_USERNAME or not SMTP_PASSWORD or not recipients:
        return

    message = EmailMessage()
    message["Subject"] = "Intruder Alert: Unauthorized person detected"
    message["From"] = EMAIL_FROM
    message["To"] = ", ".join(recipients)
    message.set_content(
        f"Unauthorized person detected.\nCamera: {alert.camera_id}\nTime: {alert.timestamp}\nMessage: {alert.message}\nDashboard: {dashboard_base_url}"
    )
    message.add_attachment(read_file_bytes(alert.image_path), maintype="image", subtype="jpeg", filename=alert.file_name)

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
        server.starttls()
        server.login(SMTP_USERNAME, SMTP_PASSWORD)
        server.send_message(message)


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        get_or_create_settings(db)
    yield


app = FastAPI(lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(HTTPException)
async def http_exception_handler(_: Request, exc: HTTPException):
    return JSONResponse(status_code=exc.status_code, content={"message": exc.detail})


@app.get("/api/health")
def health():
    return {"status": "ok"}


@app.post("/api/auth/signup", status_code=201)
def signup(request: SignupRequest, db: Session = Depends(get_db)):
    email = normalize_email(request.email)
    existing = db.scalar(select(UserAccount).where(UserAccount.email == email))
    if existing:
        raise HTTPException(status_code=409, detail="Email is already registered.")

    user = UserAccount(
        full_name=request.fullName.strip(),
        email=email,
        password_hash=password_context.hash(request.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    db.query(UserSession).filter(UserSession.user_id == user.id).delete()
    session = UserSession(
        user_id=user.id,
        token=secrets.token_urlsafe(48),
        device_name=request.deviceName.strip(),
        expires_at=utc_now() + timedelta(hours=TOKEN_VALIDITY_HOURS),
    )
    db.add(session)
    db.commit()
    db.refresh(session)

    return {"token": session.token, "user": serialize_user_profile(user, session)}


@app.post("/api/auth/login")
def login(request: LoginRequest, db: Session = Depends(get_db)):
    email = normalize_email(request.email)
    user = db.scalar(select(UserAccount).where(UserAccount.email == email))
    if user is None or not password_context.verify(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials.")

    db.query(UserSession).filter(UserSession.user_id == user.id).delete()
    session = UserSession(
        user_id=user.id,
        token=secrets.token_urlsafe(48),
        device_name=request.deviceName.strip(),
        expires_at=utc_now() + timedelta(hours=TOKEN_VALIDITY_HOURS),
    )
    db.add(session)
    db.commit()
    db.refresh(session)

    return {"token": session.token, "user": serialize_user_profile(user, session)}


@app.get("/api/auth/me")
def me(session: UserSession = Depends(get_current_session)):
    return serialize_user_profile(session.user, session)


@app.post("/api/auth/logout", status_code=204)
def logout(session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    db.delete(session)
    db.commit()
    return Response(status_code=204)


@app.get("/api/alerts")
def get_alerts(session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    alerts = db.scalars(select(Alert).order_by(Alert.id.desc())).all()
    return [serialize_alert(alert) for alert in alerts]


@app.get("/api/alerts/latest")
def latest_alert(session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    alert = db.scalar(select(Alert).order_by(Alert.id.desc()).limit(1))
    return serialize_alert(alert) if alert else None


@app.get("/api/alerts/{alert_id}/image")
def alert_image(alert_id: int, session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    alert = db.get(Alert, alert_id)
    if alert is None:
        raise HTTPException(status_code=404, detail="Alert not found.")
    return Response(content=read_file_bytes(alert.image_path), media_type="image/jpeg")


@app.get("/api/alerts/stream")
async def alert_stream(request: Request, session: UserSession = Depends(get_current_session)):
    queue = await event_hub.subscribe()

    async def generator():
        try:
            while True:
                if await request.is_disconnected():
                    break
                name, payload = await queue.get()
                if payload is None:
                    yield "event: connected\ndata: {}\n\n"
                else:
                    yield f"event: {name}\ndata: {json.dumps(payload)}\n\n"
        finally:
            event_hub.unsubscribe(queue)

    return StreamingResponse(generator(), media_type="text/event-stream")


@app.post("/api/alerts", status_code=201)
async def create_alert(
    request: AlertRequest,
    background_tasks: BackgroundTasks,
    detector_key: Optional[str] = Header(default=None, alias="X-Detector-Key"),
    db: Session = Depends(get_db),
):
    require_detector_key(detector_key)
    image_bytes = bytes(__import__("base64").b64decode(request.imageBase64))
    image_path = store_bytes(ALERT_IMAGES_DIR, request.fileName, image_bytes)

    alert = Alert(
        camera_id=request.cameraId,
        timestamp=request.timestamp,
        image_path=image_path,
        file_name=request.fileName,
        message=request.message,
        severity="HIGH",
        status="NEW",
    )
    db.add(alert)
    db.commit()
    db.refresh(alert)

    settings = get_or_create_settings(db)
    recipients = [email for (email,) in db.query(UserAccount.email).all() if email]
    if settings.email_enabled:
        background_tasks.add_task(send_alert_email, alert, recipients, settings.dashboard_base_url)

    serialized = serialize_alert(alert)
    await event_hub.publish("created", serialized)
    return serialized


@app.patch("/api/alerts/{alert_id}/status")
async def update_alert_status(
    alert_id: int,
    request: AlertStatusUpdateRequest,
    session: UserSession = Depends(get_current_session),
    db: Session = Depends(get_db),
):
    alert = db.get(Alert, alert_id)
    if alert is None:
        raise HTTPException(status_code=404, detail="Alert not found.")

    next_status = request.status.strip().upper()
    if next_status not in {"NEW", "ACKNOWLEDGED", "RESOLVED"}:
        raise HTTPException(status_code=400, detail="Unsupported alert status.")

    alert.status = next_status
    db.commit()
    db.refresh(alert)
    serialized = serialize_alert(alert)
    await event_hub.publish("updated", serialized)
    return serialized


@app.delete("/api/alerts")
async def clear_alerts(session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    alerts = db.scalars(select(Alert)).all()
    for alert in alerts:
        try:
            Path(alert.image_path).unlink(missing_ok=True)
        except Exception:
            pass
        db.delete(alert)
    db.commit()
    await event_hub.publish("cleared", None)
    return {"status": "cleared"}


@app.get("/api/settings/system")
def get_settings(session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    return serialize_settings(db)


@app.patch("/api/settings/system")
def update_settings(
    request: SystemSettingsUpdateRequest,
    session: UserSession = Depends(get_current_session),
    db: Session = Depends(get_db),
):
    settings = get_or_create_settings(db)
    settings.email_enabled = request.emailEnabled
    settings.dashboard_base_url = request.dashboardBaseUrl.strip()
    db.add(settings)
    db.commit()
    return serialize_settings(db)


@app.post("/api/detector/heartbeat", status_code=202)
def detector_heartbeat(
    request: DetectorHeartbeatRequest,
    detector_key: Optional[str] = Header(default=None, alias="X-Detector-Key"),
    db: Session = Depends(get_db),
):
    require_detector_key(detector_key)
    status = db.scalar(select(DetectorStatus).order_by(DetectorStatus.id.desc()).limit(1))
    if status is None:
        status = DetectorStatus(camera_id=request.cameraId, last_heartbeat_at=request.timestamp, alert_cooldown_seconds=request.alertCooldownSeconds)
        db.add(status)
    else:
        status.camera_id = request.cameraId
        status.last_heartbeat_at = request.timestamp
        status.alert_cooldown_seconds = request.alertCooldownSeconds
    db.commit()
    return {"status": "recorded"}


@app.get("/api/detector/faces")
def detector_faces(
    detector_key: Optional[str] = Header(default=None, alias="X-Detector-Key"),
    db: Session = Depends(get_db),
):
    require_detector_key(detector_key)
    faces = db.scalars(select(AuthorizedFace).order_by(AuthorizedFace.id.asc())).all()
    return [{"id": face.id, "displayName": face.display_name, "updatedAt": face.updated_at} for face in faces]


@app.get("/api/detector/faces/{face_id}/image")
def detector_face_image(
    face_id: int,
    detector_key: Optional[str] = Header(default=None, alias="X-Detector-Key"),
    db: Session = Depends(get_db),
):
    require_detector_key(detector_key)
    face = db.get(AuthorizedFace, face_id)
    if face is None:
        raise HTTPException(status_code=404, detail="Authorized face not found.")
    return Response(content=read_file_bytes(face.image_path), media_type="image/jpeg")


@app.get("/api/faces/me")
def get_my_face(session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    face = db.scalar(select(AuthorizedFace).where(AuthorizedFace.user_id == session.user_id))
    return serialize_face(face)


@app.post("/api/faces/me", status_code=201)
def upload_my_face(
    image: UploadFile = File(...),
    session: UserSession = Depends(get_current_session),
    db: Session = Depends(get_db),
):
    content_type = image.content_type or ""
    if not content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Only image uploads are supported.")
    content = image.file.read()
    if len(content) > 2 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Image is too large. Keep it under 2 MB.")

    extension = Path(image.filename or "face.jpg").suffix.lower()
    if extension not in {".jpg", ".jpeg", ".png"}:
        extension = ".jpg"

    file_path = store_bytes(AUTHORIZED_FACES_DIR, f"user-{session.user_id}{extension}", content)
    face = db.scalar(select(AuthorizedFace).where(AuthorizedFace.user_id == session.user_id))
    if face is None:
        face = AuthorizedFace(
            user_id=session.user_id,
            display_name=slugify_name(session.user.full_name),
            image_path=file_path,
            updated_at=utc_now().isoformat(),
        )
        db.add(face)
    else:
        face.display_name = slugify_name(session.user.full_name)
        face.image_path = file_path
        face.updated_at = utc_now().isoformat()
    db.commit()
    db.refresh(face)
    return serialize_face(face)


@app.delete("/api/faces/me", status_code=204)
def delete_my_face(session: UserSession = Depends(get_current_session), db: Session = Depends(get_db)):
    face = db.scalar(select(AuthorizedFace).where(AuthorizedFace.user_id == session.user_id))
    if face is not None:
        try:
            Path(face.image_path).unlink(missing_ok=True)
        except Exception:
            pass
        db.delete(face)
        db.commit()
    return Response(status_code=204)
