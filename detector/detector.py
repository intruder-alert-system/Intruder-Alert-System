import base64
import logging
import os
import queue
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import List

os.environ.setdefault("OPENCV_OPENCL_RUNTIME", "disabled")

import cv2
import face_recognition
import requests
from dotenv import load_dotenv

try:
    import winsound
except ImportError:
    winsound = None


BASE_DIR = Path(__file__).resolve().parent
KNOWN_FACES_DIR = BASE_DIR / "known_faces"
MANAGED_FACES_DIR = BASE_DIR / "managed_faces"
CAPTURES_DIR = BASE_DIR / "captures"
LOGS_DIR = BASE_DIR / "logs"

cv2.ocl.setUseOpenCL(False)

LOGS_DIR.mkdir(exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler(LOGS_DIR / "detector.log", encoding="utf-8"),
    ],
)
LOGGER = logging.getLogger("intruder_detector")


class KnownFace:
    def __init__(self, name: str, encoding) -> None:
        self.name = name
        self.encoding = encoding


class IntruderDetector:
    def __init__(self) -> None:
        load_dotenv(BASE_DIR / ".env")
        CAPTURES_DIR.mkdir(exist_ok=True)

        # These settings keep the detector easy to tune without touching code.
        self.backend_alert_url = os.getenv(
            "BACKEND_ALERT_URL", "http://localhost:8080/api/alerts"
        )
        self.backend_health_url = os.getenv(
            "BACKEND_HEALTH_URL", self.backend_alert_url.removesuffix("/api/alerts") + "/api/health"
        )
        self.detector_heartbeat_url = os.getenv(
            "DETECTOR_HEARTBEAT_URL", self.backend_alert_url.removesuffix("/api/alerts") + "/api/detector/heartbeat"
        )
        self.detector_faces_url = os.getenv(
            "DETECTOR_FACES_URL", self.backend_alert_url.removesuffix("/api/alerts") + "/api/detector/faces"
        )
        self.detector_api_key = os.getenv("DETECTOR_API_KEY", "change-me-detector-key")
        self.camera_id = os.getenv("CAMERA_ID", "CAM-01")
        self.camera_index = int(os.getenv("CAMERA_INDEX", "0"))
        self.alert_cooldown_seconds = int(os.getenv("ALERT_COOLDOWN_SECONDS", "20"))
        self.face_match_tolerance = float(os.getenv("FACE_MATCH_TOLERANCE", "0.48"))
        self.process_every_n_frames = max(1, int(os.getenv("PROCESS_EVERY_N_FRAMES", "3")))
        self.frame_resize_scale = float(os.getenv("FRAME_RESIZE_SCALE", "0.4"))
        self.max_frame_width = int(os.getenv("MAX_FRAME_WIDTH", "640"))
        self.recognition_min_interval_seconds = max(
            0.0, float(os.getenv("RECOGNITION_MIN_INTERVAL_SECONDS", "0.25"))
        )
        self.max_recognition_roi_side = max(
            96, int(os.getenv("MAX_RECOGNITION_ROI_SIDE", "320"))
        )
        self.face_detection_upsample = max(
            0, int(os.getenv("FACE_DETECTION_UPSAMPLE", "0"))
        )
        self.live_detection_scale = float(os.getenv("LIVE_DETECTION_SCALE", "0.35"))
        self.live_detection_every_n_frames = max(
            1, int(os.getenv("LIVE_DETECTION_EVERY_N_FRAMES", "3"))
        )
        self.display_scale = float(os.getenv("DISPLAY_SCALE", "0.7"))
        self.display_window_enabled = (
            os.getenv("DISPLAY_WINDOW_ENABLED", "true").lower() == "true"
        )
        self.recognition_roi_padding = max(
            0, int(os.getenv("RECOGNITION_ROI_PADDING", "16"))
        )
        self.min_recognition_face_size = max(
            64, int(os.getenv("MIN_RECOGNITION_FACE_SIZE", "128"))
        )
        self.unknown_confirmation_frames = max(
            1, int(os.getenv("UNKNOWN_CONFIRMATION_FRAMES", "2"))
        )
        self.alert_beep_enabled = os.getenv("ALERT_BEEP_ENABLED", "true").lower() == "true"
        self.alert_beep_frequency = max(
            200, int(os.getenv("ALERT_BEEP_FREQUENCY", "1400"))
        )
        self.alert_beep_duration_ms = max(
            50, int(os.getenv("ALERT_BEEP_DURATION_MS", "350"))
        )
        self.backend_retry_interval_seconds = max(
            1, int(os.getenv("BACKEND_RETRY_INTERVAL_SECONDS", "30"))
        )
        self.heartbeat_interval_seconds = max(
            5, int(os.getenv("HEARTBEAT_INTERVAL_SECONDS", "15"))
        )
        self.face_sync_interval_seconds = max(
            20, int(os.getenv("FACE_SYNC_INTERVAL_SECONDS", "60"))
        )
        self.last_alert_time = 0.0
        self.next_backend_retry_time = 0.0
        self.backend_warning_active = False
        self.frame_counter = 0
        self.last_detections: list[dict] = []
        self.last_live_detections: list[dict] = []
        self.synced_face_versions: dict[int, str] = {}
        self.frame_lock = threading.Lock()
        self.camera_lock = threading.Lock()
        self.results_lock = threading.Lock()
        self.known_faces_lock = threading.Lock()
        self.stop_event = threading.Event()
        self.alert_queue: queue.Queue = queue.Queue(maxsize=1)
        self.latest_camera_frame = None
        self.camera_frame_id = 0
        self.last_displayed_camera_frame_id = 0
        self.pending_frame = None
        self.pending_frame_id = 0
        self.last_processed_frame_id = 0
        self.last_recognition_time = 0.0
        self.last_recognition_request_time = 0.0
        self.recognition_result_id = 0
        self.last_seen_result_id = 0
        self.unknown_streak = 0
        self.last_recognition_summary = ""
        cascade_path = Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"
        self.face_cascade = cv2.CascadeClassifier(str(cascade_path))
        if self.face_cascade.empty():
            raise RuntimeError("Could not load OpenCV Haar cascade for live face detection.")
        MANAGED_FACES_DIR.mkdir(exist_ok=True)
        self.known_faces = self._load_known_faces()

    def _person_key_from_path(self, image_path: Path) -> str:
        stem = image_path.stem.strip()

        # Allow multiple files per person like harry_1.jpg, harry_2.jpg.
        if "_" in stem:
            return stem.rsplit("_", 1)[0]

        return stem

    def _load_known_faces(self) -> List[KnownFace]:
        known_faces: List[KnownFace] = []

        for faces_directory in (KNOWN_FACES_DIR, MANAGED_FACES_DIR):
            for image_path in sorted(faces_directory.glob("*")):
                try:
                    if image_path.suffix.lower() not in {".jpg", ".jpeg", ".png"}:
                        continue

                    # Each image file contributes one reference encoding for an authorized person.
                    image = face_recognition.load_image_file(image_path)
                    encodings = face_recognition.face_encodings(image)

                    if not encodings:
                        LOGGER.warning("No face found in %s. Skipping file.", image_path.name)
                        continue

                    known_faces.append(
                        KnownFace(name=self._person_key_from_path(image_path), encoding=encodings[0])
                    )
                    LOGGER.info("Loaded authorized face sample from %s", image_path.name)
                except Exception as exc:
                    LOGGER.warning("Could not load %s: %s", image_path.name, exc)

        LOGGER.info("Loaded %s authorized face sample(s).", len(known_faces))
        return known_faces

    def _recognize_face(self, face_encoding) -> tuple[bool, str]:
        with self.known_faces_lock:
            known_faces = list(self.known_faces)

        if not known_faces:
            return False, "Unknown"

        matches = face_recognition.compare_faces(
            [face.encoding for face in known_faces],
            face_encoding,
            tolerance=self.face_match_tolerance,
        )

        if True not in matches:
            return False, "Unknown"

        face_distances = face_recognition.face_distance(
            [face.encoding for face in known_faces], face_encoding
        )
        best_match_index = face_distances.argmin()
        return True, known_faces[best_match_index].name

    def _save_capture(self, frame) -> Path:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        filename = f"intruder_{timestamp}.jpg"
        capture_path = CAPTURES_DIR / filename
        cv2.imwrite(str(capture_path), frame)
        return capture_path

    def _encode_image(self, image_path: Path) -> str:
        with image_path.open("rb") as image_file:
            return base64.b64encode(image_file.read()).decode("utf-8")

    def _send_alert(self, frame) -> None:
        now = time.time()
        # Cooldown prevents repeated alerts from flooding the dashboard.
        if now - self.last_alert_time < self.alert_cooldown_seconds:
            return

        if now < self.next_backend_retry_time:
            return

        self.last_alert_time = now

        try:
            self.alert_queue.put_nowait(frame.copy())
        except queue.Full:
            return

    def _deliver_alert(self, frame) -> None:
        capture_path = self._save_capture(frame)

        timestamp = datetime.now(timezone.utc).isoformat()
        payload = {
            "cameraId": self.camera_id,
            "timestamp": timestamp,
            "imageBase64": self._encode_image(capture_path),
            "fileName": capture_path.name,
            "message": "Unauthorized person detected near secured area.",
        }

        try:
            response = requests.post(
                self.backend_alert_url,
                json=payload,
                headers={"X-Detector-Key": self.detector_api_key},
                timeout=5,
            )
            response.raise_for_status()
            if self.backend_warning_active:
                LOGGER.info("Backend connection restored. Alert delivery resumed.")
            self.backend_warning_active = False
            self.next_backend_retry_time = 0.0
            LOGGER.info("Intruder sent to backend at %s", timestamp)
        except requests.RequestException as exc:
            retry_at = time.time() + self.backend_retry_interval_seconds
            self.next_backend_retry_time = retry_at
            if not self.backend_warning_active:
                LOGGER.warning(
                    "Alert API is unavailable. Saved local capture %s and pausing retries for %s seconds.",
                    capture_path.name,
                    self.backend_retry_interval_seconds,
                )
                LOGGER.warning("Backend request error: %s", exc)
            self.backend_warning_active = True

    def _play_alert_beep(self) -> None:
        if not self.alert_beep_enabled or winsound is None:
            return

        try:
            winsound.Beep(self.alert_beep_frequency, self.alert_beep_duration_ms)
        except RuntimeError:
            pass

    def _check_backend_connection(self) -> None:
        try:
            response = requests.get(self.backend_health_url, timeout=3)
            response.raise_for_status()
        except requests.RequestException as exc:
            LOGGER.warning(
                "Backend health endpoint is not reachable at %s. Dashboard snapshots and timestamps will not update.",
                self.backend_health_url,
            )
            LOGGER.warning("Backend request error: %s", exc)

    def _send_heartbeat(self) -> None:
        payload = {
            "cameraId": self.camera_id,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "alertCooldownSeconds": self.alert_cooldown_seconds,
        }

        try:
            requests.post(
                self.detector_heartbeat_url,
                json=payload,
                headers={"X-Detector-Key": self.detector_api_key},
                timeout=3,
            ).raise_for_status()
        except requests.RequestException as exc:
            LOGGER.warning("Detector heartbeat failed: %s", exc)

    def _sync_managed_faces(self) -> None:
        try:
            response = requests.get(
                self.detector_faces_url,
                headers={"X-Detector-Key": self.detector_api_key},
                timeout=5,
            )
            response.raise_for_status()
            faces = response.json()
        except requests.RequestException:
            LOGGER.warning("Managed face sync metadata fetch failed.")
            return

        active_file_names = set()

        for face in faces:
            face_id = face["id"]
            display_name = face["displayName"]
            updated_at = face["updatedAt"]
            file_name = f"{display_name}_{face_id}.jpg"
            active_file_names.add(file_name)
            file_path = MANAGED_FACES_DIR / file_name

            if self.synced_face_versions.get(face_id) == updated_at and file_path.exists():
                continue

            try:
                image_response = requests.get(
                    f"{self.detector_faces_url}/{face_id}/image",
                    headers={"X-Detector-Key": self.detector_api_key},
                    timeout=10,
                )
                image_response.raise_for_status()
                file_path.write_bytes(image_response.content)
                self.synced_face_versions[face_id] = updated_at
            except requests.RequestException:
                LOGGER.warning("Managed face image download failed for face id %s", face_id)
                continue

        for existing_file in MANAGED_FACES_DIR.glob("*"):
            if existing_file.name not in active_file_names:
                existing_file.unlink(missing_ok=True)

        active_face_ids = {face["id"] for face in faces}
        self.synced_face_versions = {
            face_id: updated_at
            for face_id, updated_at in self.synced_face_versions.items()
            if face_id in active_face_ids
        }

        try:
            refreshed_faces = self._load_known_faces()
        except RuntimeError:
            return

        with self.known_faces_lock:
            self.known_faces = refreshed_faces
        LOGGER.info("Managed face sync completed. Authorized faces refreshed.")

    def _analyze_frame(self, frame) -> list[dict]:
        live_detections = self._detect_live_faces(frame)
        frame_height, frame_width = frame.shape[:2]
        detections: list[dict] = []

        for live_detection in live_detections:
            padding = self.recognition_roi_padding
            left = max(0, live_detection["left"] - padding)
            top = max(0, live_detection["top"] - padding)
            right = min(frame_width, live_detection["right"] + padding)
            bottom = min(frame_height, live_detection["bottom"] + padding)

            roi = frame[top:bottom, left:right]
            if roi.size == 0:
                continue

            roi_height, roi_width = roi.shape[:2]
            largest_side = max(roi_height, roi_width)
            if largest_side < self.min_recognition_face_size:
                resize_ratio = self.min_recognition_face_size / largest_side
                roi = cv2.resize(
                    roi,
                    (
                        max(1, int(roi_width * resize_ratio)),
                        max(1, int(roi_height * resize_ratio)),
                    ),
                )
                roi_height, roi_width = roi.shape[:2]
                largest_side = max(roi_height, roi_width)

            if largest_side > self.max_recognition_roi_side:
                resize_ratio = self.max_recognition_roi_side / largest_side
                roi = cv2.resize(
                    roi,
                    (
                        max(1, int(roi_width * resize_ratio)),
                        max(1, int(roi_height * resize_ratio)),
                    ),
                    interpolation=cv2.INTER_AREA,
                )

            rgb_roi = cv2.cvtColor(roi, cv2.COLOR_BGR2RGB)
            roi_face_locations = face_recognition.face_locations(
                rgb_roi,
                number_of_times_to_upsample=self.face_detection_upsample,
                model="hog",
            )

            if not roi_face_locations:
                continue

            roi_face_encodings = face_recognition.face_encodings(
                rgb_roi, roi_face_locations
            )
            if not roi_face_encodings:
                continue

            authorized, name = self._recognize_face(roi_face_encodings[0])
            detections.append(
                {
                    "top": top,
                    "right": right,
                    "bottom": bottom,
                    "left": left,
                    "authorized": authorized,
                    "name": name if authorized else "Unknown",
                }
            )

        return detections

    def _recognition_worker(self) -> None:
        while not self.stop_event.is_set():
            try:
                frame_to_process = None

                with self.frame_lock:
                    if self.pending_frame_id > self.last_processed_frame_id:
                        frame_to_process = self.pending_frame.copy()
                        self.last_processed_frame_id = self.pending_frame_id

                if frame_to_process is None:
                    time.sleep(0.01)
                    continue

                detections = self._analyze_frame(frame_to_process)

                with self.results_lock:
                    self.last_detections = detections
                    self.last_recognition_time = time.time()
                    self.recognition_result_id += 1

                if detections:
                    summary = ", ".join(
                        detection["name"] if detection["authorized"] else "Unknown"
                        for detection in detections
                    )
                    if summary != self.last_recognition_summary:
                        LOGGER.info("Recognition update: %s", summary)
                        self.last_recognition_summary = summary
                elif self.last_recognition_summary:
                    LOGGER.info("Recognition update: no faces recognized")
                    self.last_recognition_summary = ""
            except Exception as exc:
                LOGGER.exception("Recognition worker error: %s", exc)
                time.sleep(0.5)

    def _alert_worker(self) -> None:
        while not self.stop_event.is_set():
            try:
                try:
                    frame_to_send = self.alert_queue.get(timeout=0.1)
                except queue.Empty:
                    continue

                try:
                    self._play_alert_beep()
                    self._deliver_alert(frame_to_send)
                finally:
                    self.alert_queue.task_done()
            except Exception as exc:
                LOGGER.exception("Alert worker error: %s", exc)
                time.sleep(0.5)

    def _heartbeat_worker(self) -> None:
        while not self.stop_event.is_set():
            try:
                self._send_heartbeat()
            except Exception as exc:
                LOGGER.exception("Heartbeat worker error: %s", exc)
            self.stop_event.wait(self.heartbeat_interval_seconds)

    def _face_sync_worker(self) -> None:
        while not self.stop_event.is_set():
            try:
                self._sync_managed_faces()
            except Exception as exc:
                LOGGER.exception("Face sync worker error: %s", exc)
            self.stop_event.wait(self.face_sync_interval_seconds)

    def _capture_worker(self, video_capture) -> None:
        while not self.stop_event.is_set():
            try:
                success, frame = video_capture.read()
                if not success or frame is None:
                    LOGGER.warning("Webcam frame read failed.")
                    time.sleep(0.05)
                    continue
                if getattr(frame, "size", 0) == 0:
                    LOGGER.warning("Webcam returned an empty frame.")
                    time.sleep(0.05)
                    continue

                with self.camera_lock:
                    self.latest_camera_frame = frame
                    self.camera_frame_id += 1
            except Exception as exc:
                LOGGER.exception("Capture worker error: %s", exc)
                time.sleep(0.5)

    def _detect_live_faces(self, frame) -> list[dict]:
        if frame is None or getattr(frame, "size", 0) == 0:
            return []

        scale = self.live_detection_scale
        try:
            small_frame = cv2.resize(frame, (0, 0), fx=scale, fy=scale)
            if getattr(small_frame, "size", 0) == 0:
                return []

            gray_small_frame = cv2.cvtColor(small_frame, cv2.COLOR_BGR2GRAY)
            faces = self.face_cascade.detectMultiScale(
                gray_small_frame,
                scaleFactor=1.1,
                minNeighbors=5,
                minSize=(30, 30),
            )
        except cv2.error as exc:
            LOGGER.warning("Live face detection skipped due to OpenCV frame error: %s", exc)
            return []

        live_detections: list[dict] = []
        for left, top, width, height in faces:
            live_detections.append(
                {
                    "left": int(left / scale),
                    "top": int(top / scale),
                    "right": int((left + width) / scale),
                    "bottom": int((top + height) / scale),
                }
            )

        if live_detections:
            return live_detections

        try:
            rgb_small_frame = cv2.cvtColor(small_frame, cv2.COLOR_BGR2RGB)
            fallback_locations = face_recognition.face_locations(
                rgb_small_frame,
                number_of_times_to_upsample=max(0, self.face_detection_upsample),
                model="hog",
            )
        except Exception as exc:
            LOGGER.warning("Fallback face detection failed: %s", exc)
            return []

        for top, right, bottom, left in fallback_locations:
            live_detections.append(
                {
                    "left": int(left / scale),
                    "top": int(top / scale),
                    "right": int(right / scale),
                    "bottom": int(bottom / scale),
                }
            )

        return live_detections

    def _box_center(self, detection: dict) -> tuple[float, float]:
        return (
            (detection["left"] + detection["right"]) / 2,
            (detection["top"] + detection["bottom"]) / 2,
        )

    def _intersection_over_union(self, first_box: dict, second_box: dict) -> float:
        left = max(first_box["left"], second_box["left"])
        top = max(first_box["top"], second_box["top"])
        right = min(first_box["right"], second_box["right"])
        bottom = min(first_box["bottom"], second_box["bottom"])

        if right <= left or bottom <= top:
            return 0.0

        intersection_area = (right - left) * (bottom - top)
        first_area = (first_box["right"] - first_box["left"]) * (
            first_box["bottom"] - first_box["top"]
        )
        second_area = (second_box["right"] - second_box["left"]) * (
            second_box["bottom"] - second_box["top"]
        )
        union_area = first_area + second_area - intersection_area
        if union_area <= 0:
            return 0.0

        return intersection_area / union_area

    def _match_live_detection(
        self, live_detection: dict, recognized_detections: list[dict]
    ):
        if not recognized_detections:
            return None

        live_center_x, live_center_y = self._box_center(live_detection)
        live_width = max(1, live_detection["right"] - live_detection["left"])
        live_height = max(1, live_detection["bottom"] - live_detection["top"])
        max_distance = max(live_width, live_height) * 1.5

        best_match = None
        best_score = None

        for recognized_detection in recognized_detections:
            recognized_center_x, recognized_center_y = self._box_center(
                recognized_detection
            )
            distance = (
                (live_center_x - recognized_center_x) ** 2
                + (live_center_y - recognized_center_y) ** 2
            ) ** 0.5
            overlap = self._intersection_over_union(live_detection, recognized_detection)
            score = overlap * 1000 - distance

            if (overlap > 0.08 or distance <= max_distance) and (
                best_score is None or score > best_score
            ):
                best_match = recognized_detection
                best_score = score

        if best_match is None and len(recognized_detections) == 1:
            best_match = recognized_detections[0]

        return best_match

    def run(self) -> None:
        self._check_backend_connection()
        LOGGER.info("Opening detector camera index %s.", self.camera_index)
        video_capture = cv2.VideoCapture(self.camera_index, cv2.CAP_DSHOW)

        if not video_capture.isOpened():
            raise RuntimeError(f"Could not open webcam at camera index {self.camera_index}.")

        video_capture.set(cv2.CAP_PROP_FRAME_WIDTH, self.max_frame_width)
        video_capture.set(cv2.CAP_PROP_FRAME_HEIGHT, int(self.max_frame_width * 0.75))
        video_capture.set(cv2.CAP_PROP_BUFFERSIZE, 1)

        capture_thread = threading.Thread(
            target=self._capture_worker, args=(video_capture,), daemon=True
        )
        recognition_thread = threading.Thread(
            target=self._recognition_worker, daemon=True
        )
        alert_thread = threading.Thread(target=self._alert_worker, daemon=True)
        heartbeat_thread = threading.Thread(target=self._heartbeat_worker, daemon=True)
        face_sync_thread = threading.Thread(target=self._face_sync_worker, daemon=True)
        capture_thread.start()
        recognition_thread.start()
        alert_thread.start()
        heartbeat_thread.start()
        face_sync_thread.start()

        LOGGER.info("Detector started. Press 'q' to quit.")

        try:
            while True:
                with self.camera_lock:
                    if self.camera_frame_id == self.last_displayed_camera_frame_id:
                        frame = None
                    else:
                        frame = self.latest_camera_frame.copy()
                        self.last_displayed_camera_frame_id = self.camera_frame_id

                if frame is None or getattr(frame, "size", 0) == 0:
                    time.sleep(0.005)
                    continue

                should_process = self.frame_counter % self.process_every_n_frames == 0
                now = time.time()
                self.frame_counter += 1

                if (
                    should_process
                    and now - self.last_recognition_request_time
                    >= self.recognition_min_interval_seconds
                ):
                    with self.frame_lock:
                        # Keep only the newest frame so stale frames do not queue up.
                        self.pending_frame = frame.copy()
                        self.pending_frame_id += 1
                        self.last_recognition_request_time = now

                with self.results_lock:
                    recognized_detections = list(self.last_detections)
                    last_recognition_time = self.last_recognition_time
                    recognition_result_id = self.recognition_result_id

                has_unknown_recognition = any(
                    detection["authorized"] is False
                    for detection in recognized_detections
                )

                should_refresh_live_detection = (
                    self.frame_counter % self.live_detection_every_n_frames == 0
                )
                if should_refresh_live_detection or not self.last_live_detections:
                    self.last_live_detections = self._detect_live_faces(frame)

                live_detections = list(self.last_live_detections)

                unknown_visible = False

                for detection in live_detections:
                    top = detection["top"]
                    right = detection["right"]
                    bottom = detection["bottom"]
                    left = detection["left"]
                    matched_detection = self._match_live_detection(
                        detection, recognized_detections
                    )
                    authorized = matched_detection["authorized"] if matched_detection else None
                    if matched_detection:
                        label_text = matched_detection["name"]
                    elif time.time() - last_recognition_time > 1.0:
                        label_text = "Unknown"
                    else:
                        label_text = "Scanning"

                    if authorized is True:
                        box_color = (60, 180, 75)
                    elif authorized is False:
                        box_color = (0, 0, 255)
                        unknown_visible = True
                    else:
                        box_color = (0, 215, 255)

                    cv2.rectangle(frame, (left, top), (right, bottom), box_color, 2)
                    cv2.putText(
                        frame,
                        label_text,
                        (left, max(top - 10, 25)),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.8,
                        box_color,
                        2,
                    )

                status_text = "System Secure"
                status_color = (60, 180, 75)

                if recognition_result_id != self.last_seen_result_id:
                    self.last_seen_result_id = recognition_result_id
                    if has_unknown_recognition:
                        self.unknown_streak += 1
                    else:
                        self.unknown_streak = 0

                if (unknown_visible or has_unknown_recognition) and self.unknown_streak > 0:
                    status_text = "Verifying Face"
                    status_color = (0, 215, 255)

                if self.unknown_streak >= self.unknown_confirmation_frames:
                    status_text = "Intruder Detected"
                    status_color = (0, 0, 255)
                    self._send_alert(frame)

                cv2.putText(
                    frame,
                    status_text,
                    (20, 40),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    1,
                    status_color,
                    2,
                )

                if self.display_window_enabled:
                    display_frame = frame
                    if 0 < self.display_scale < 1:
                        display_frame = cv2.resize(
                            frame, (0, 0), fx=self.display_scale, fy=self.display_scale
                        )

                    cv2.imshow("Smart Intruder Alert System", display_frame)

                    if cv2.waitKey(1) & 0xFF == ord("q"):
                        break
                else:
                    time.sleep(0.01)
        except Exception as exc:
            LOGGER.exception("Detector main loop crashed: %s", exc)
            raise
        finally:
            self.stop_event.set()
            capture_thread.join(timeout=1)
            recognition_thread.join(timeout=1)
            alert_thread.join(timeout=1)
            heartbeat_thread.join(timeout=1)
            face_sync_thread.join(timeout=1)
            video_capture.release()
            if self.display_window_enabled:
                cv2.destroyAllWindows()
            LOGGER.info("Detector shut down.")


if __name__ == "__main__":
    detector = IntruderDetector()
    detector.run()
