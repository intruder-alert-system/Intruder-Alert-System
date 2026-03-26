# Smart Intruder Alert System

Demo-ready college showcase project built as a 3-part system:

- `detector/`: Python webcam monitor with face recognition and alert posting
- `backend/`: Spring Boot API with in-memory alert storage
- `frontend/`: React dashboard for live monitoring

## Architecture

1. The Python detector reads faces from `detector/known_faces/`.
2. When an unknown face is seen, it saves a snapshot in `detector/captures/`.
3. The detector sends the alert data to the Spring Boot backend.
4. The React dashboard polls the backend and renders the latest alerts.

## Demo Flow

1. Add authorized face images to `detector/known_faces/`.
2. Start the backend.
3. Start the frontend.
4. Start the detector.
5. Show an authorized user first.
6. Show an unauthorized user to trigger the alert dashboard.

## Setup

### Backend

- Java 17+
- Maven 3.9+

```powershell
cd backend
mvn spring-boot:run
```

Backend runs on `http://localhost:8080`.

If Maven is not installed on your machine but the project has already been built once, you can use one of the included launchers:

```powershell
cd backend
.\run-backend.cmd
```

PowerShell users can also run `.\run-backend.ps1` if local execution policy allows scripts.

The launchers will try `mvnw`, then `mvn`, and finally fall back to `target/intruder-alert-0.0.1-SNAPSHOT.jar` if it already exists.

### Frontend

- Node.js 18+

```powershell
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

On Windows PowerShell, `npm` can be blocked by execution policy because `npm.ps1` is preferred. If that happens, use:

```powershell
cd frontend
.\run-frontend.cmd
```

### Detector

- Python 3.11 recommended

Recommended Windows setup:

```powershell
cd detector
.\setup-detector.cmd
```

Run:

```powershell
.\run-detector.cmd
```

The detector launcher will create `detector/.env` from `.env.example` automatically if it is missing.

## Notes

- The detector uses OpenCV face detection plus LBPH recognition so it is easier to run on Windows for live demos.
- Alert suppression is built in so the same person does not spam the system every frame.
- If the backend is offline, the detector still saves local captures and backs off before retrying API delivery.
- The backend uses in-memory storage to keep the project simple for a showcase.
