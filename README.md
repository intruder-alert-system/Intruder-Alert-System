# Smart Intruder Alert System

Demo-ready college showcase project built as a 3-part system:

- `detector/`: Python webcam monitor with face recognition and alert posting
- `backend/`: FastAPI API with persistent alerts, auth, detector sync, and settings
- `frontend/`: React dashboard with login/signup, live alert feed, and protected monitoring views

## Architecture

1. The Python detector reads faces from `detector/known_faces/`.
2. When an unknown face is seen, it saves a snapshot in `detector/captures/`.
3. The detector sends the alert data to the FastAPI backend using a detector API key.
4. The backend persists alerts and enforces one active dashboard session per user.
5. The React dashboard authenticates before loading incidents and receives live incident updates.

## Demo Flow

1. Add authorized face images to `detector/known_faces/`.
2. Start the backend.
3. Start the frontend.
4. Start the detector.
5. Show an authorized user first.
6. Show an unauthorized user to trigger the alert dashboard.

## Setup

### Backend

- Python 3.11 recommended

```powershell
cd backend
.\run-backend.cmd
```

Backend runs on `http://localhost:8080`.

Runtime configuration lives in `backend/.env`.

- `DETECTOR_API_KEY` must match `DETECTOR_API_KEY` in `detector/.env`
- `DATABASE_URL` can point to PostgreSQL, but the included default uses local SQLite so the app still runs without extra setup
- Gmail alerting is optional and disabled by default

Set up the backend environment if needed:

```powershell
cd backend
.\setup-backend.cmd
```

PowerShell users can also run `.\run-backend.ps1` if local execution policy allows scripts.

### Frontend

- Node.js 18+

```powershell
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

Optional environment variable:

```powershell
$env:VITE_API_BASE_URL="http://localhost:8080"
```

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

Important:

- `DETECTOR_API_KEY` in `detector/.env` must match the backend detector key
- the detector still runs on the machine with the webcam, even if the frontend/backend are hosted

## Gmail Alerts

To enable email delivery when an intruder is detected, configure these values in
`backend/.env`:

- `EMAIL_ENABLED=true`
- `EMAIL_FROM=yourgmail@gmail.com`
- `SMTP_USERNAME=yourgmail@gmail.com`
- `SMTP_PASSWORD=your-gmail-app-password`
- `EMAIL_DASHBOARD_BASE_URL=` your deployed frontend URL

Use a Gmail app password, not your normal Gmail password.

## Notes

- The detector uses OpenCV face detection plus LBPH recognition so it is easier to run on Windows for live demos.
- Alert suppression is built in so the same person does not spam the system every frame.
- If the backend is offline, the detector still saves local captures and backs off before retrying API delivery.
- the frontend now requires an authenticated user session before loading alerts
- logging in on a new device revokes the previous dashboard session for that same user
- alerts can be acknowledged or resolved from the dashboard
