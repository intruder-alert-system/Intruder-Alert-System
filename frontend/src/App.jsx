import { useEffect, useMemo, useRef, useState } from "react";

const API_ROOT = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const ALERTS_URL = `${API_ROOT}/api/alerts`;
const ALERT_STREAM_URL = `${ALERTS_URL}/stream`;
const AUTH_URL = `${API_ROOT}/api/auth`;
const SETTINGS_URL = `${API_ROOT}/api/settings/system`;
const FACES_URL = `${API_ROOT}/api/faces`;
const TOKEN_STORAGE_KEY = "intruder-alert-token";

function formatTimestamp(timestamp) {
  if (!timestamp) {
    return "No incidents yet";
  }

  return new Date(timestamp).toLocaleString();
}

function buildDeviceName() {
  const platform =
    navigator.userAgentData?.platform || navigator.platform || "Unknown device";
  return `${platform} Dashboard`;
}

function upsertAlert(alerts, nextAlert) {
  const others = alerts.filter((alert) => alert.id !== nextAlert.id);
  return [nextAlert, ...others].sort((first, second) => second.id - first.id);
}

function buildAlertImageUrl(alertId, token) {
  return `${ALERTS_URL}/${alertId}/image?access_token=${encodeURIComponent(token)}`;
}

async function apiRequest(path, options = {}, token) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers ?? {}),
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(data?.message || data?.error || `Request failed with ${response.status}`);
  }

  return data;
}

function AuthPanel({ mode, form, onChange, onSubmit, loading, error, switchMode }) {
  const isSignup = mode === "signup";

  return (
    <section className="auth-shell">
      <div className="auth-copy">
        <p className="eyebrow">Security Control Center</p>
        <h1>Intruder monitoring that looks production-ready.</h1>
        <p className="auth-subtitle">
          Secure the dashboard, keep one active device per user, review live
          incidents, and configure your system from one place.
        </p>
        <div className="feature-strip">
          <span>Single-device login</span>
          <span>Live incident feed</span>
          <span>Detector health tracking</span>
        </div>
      </div>

      <div className="auth-card">
        <div className="auth-toggle">
          <button
            className={mode === "login" ? "auth-toggle-button active" : "auth-toggle-button"}
            onClick={() => switchMode("login")}
            type="button"
          >
            Login
          </button>
          <button
            className={isSignup ? "auth-toggle-button active" : "auth-toggle-button"}
            onClick={() => switchMode("signup")}
            type="button"
          >
            Sign Up
          </button>
        </div>

        <form className="auth-form" onSubmit={onSubmit}>
          {isSignup ? (
            <label>
              Full Name
              <input
                name="fullName"
                value={form.fullName}
                onChange={onChange}
                placeholder="Shiv Sharma"
                required
              />
            </label>
          ) : null}

          <label>
            Email
            <input
              name="email"
              type="email"
              value={form.email}
              onChange={onChange}
              placeholder="you@example.com"
              required
            />
          </label>

          <label>
            Password
            <input
              name="password"
              type="password"
              value={form.password}
              onChange={onChange}
              placeholder="Minimum 8 characters"
              required
            />
          </label>

          <label>
            Device Name
            <input
              name="deviceName"
              value={form.deviceName}
              onChange={onChange}
              placeholder="Office Laptop"
              required
            />
          </label>

          {error ? <p className="form-error">{error}</p> : null}

          <button className="primary-button" type="submit" disabled={loading}>
            {loading ? "Please wait..." : isSignup ? "Create Account" : "Enter Dashboard"}
          </button>
        </form>
      </div>
    </section>
  );
}

function SettingsPanel({
  settings,
  form,
  face,
  faceLoading,
  loading,
  onChange,
  onSubmit,
  onFaceFileChange,
  onFaceUpload,
  onFaceDelete,
}) {
  return (
    <section className="settings-grid">
      <article className="panel">
        <div className="panel-header">
          <h3>Notification Settings</h3>
          <span>{settings.emailEnabled ? "EMAIL ON" : "EMAIL OFF"}</span>
        </div>

        <form className="settings-form" onSubmit={onSubmit}>
          <label className="checkbox-row">
            <input
              type="checkbox"
              name="emailEnabled"
              checked={form.emailEnabled}
              onChange={onChange}
            />
            <span>Enable Gmail notifications for new intruder alerts</span>
          </label>

          <label>
            Dashboard URL used in emails
            <input
              name="dashboardBaseUrl"
              value={form.dashboardBaseUrl}
              onChange={onChange}
              placeholder="https://your-vercel-app.vercel.app"
              required
            />
          </label>

          <button className="primary-button inline-action" type="submit" disabled={loading}>
            {loading ? "Saving..." : "Save Settings"}
          </button>
        </form>
      </article>

      <article className="panel">
        <div className="panel-header">
          <h3>Detector Health</h3>
          <span>{settings.detectorOnline ? "ONLINE" : "OFFLINE"}</span>
        </div>

        <div className="settings-list">
          <div className="settings-row">
            <strong>Camera ID</strong>
            <span>{settings.detectorCameraId ?? "No detector heartbeat yet"}</span>
          </div>
          <div className="settings-row">
            <strong>Last Heartbeat</strong>
            <span>{formatTimestamp(settings.lastHeartbeatAt)}</span>
          </div>
          <div className="settings-row">
            <strong>Detector Cooldown</strong>
            <span>
              {settings.detectorAlertCooldownSeconds != null
                ? `${settings.detectorAlertCooldownSeconds} sec`
                : "Unknown"}
            </span>
          </div>
        </div>
      </article>

      <article className="panel">
        <div className="panel-header">
          <h3>Authorized Face</h3>
          <span>{face.enrolled ? "ENROLLED" : "NOT ENROLLED"}</span>
        </div>

        <form className="settings-form" onSubmit={onFaceUpload}>
          <div className="settings-list">
            <div className="settings-row">
              <strong>Status</strong>
              <span>
                {face.enrolled
                  ? `Active as ${face.displayName || "authorized user"}`
                  : "No uploaded face yet"}
              </span>
            </div>
            <div className="settings-row">
              <strong>Last Updated</strong>
              <span>{formatTimestamp(face.updatedAt)}</span>
            </div>
          </div>

          <label>
            Upload one clear front-face image
            <input type="file" accept="image/*" onChange={onFaceFileChange} required />
          </label>

          <div className="incident-actions">
            <button className="primary-button inline-action" type="submit" disabled={faceLoading}>
              {faceLoading ? "Saving..." : face.enrolled ? "Replace Face" : "Enroll Face"}
            </button>
            <button
              className="secondary-button"
              type="button"
              disabled={faceLoading || !face.enrolled}
              onClick={onFaceDelete}
            >
              Remove Face
            </button>
          </div>
        </form>
      </article>

      <article className="panel">
        <div className="panel-header">
          <h3>Delivery Summary</h3>
          <span>{settings.registeredRecipients} recipient(s)</span>
        </div>

        <div className="settings-list">
          <div className="settings-row">
            <strong>Sender Account</strong>
            <span>{settings.fromEmail || "Configure in backend properties"}</span>
          </div>
          <div className="settings-row">
            <strong>Registered Users</strong>
            <span>{settings.registeredRecipients}</span>
          </div>
          <div className="settings-row">
            <strong>Authorized Faces</strong>
            <span>One managed face per user to keep storage light</span>
          </div>
        </div>
      </article>
    </section>
  );
}

function CameraPreviewPanel({
  videoRef,
  enabled,
  state,
  error,
  detectorOnline,
  onToggle,
}) {
  const isLive = state === "live";
  const isStarting = state === "starting";

  return (
    <section className="camera-preview-grid">
      <article className="panel camera-panel">
        <div className="panel-header">
          <h3>Live Camera Preview</h3>
          <span>{isLive ? "BROWSER LIVE" : enabled ? "CONNECTING" : "STOPPED"}</span>
        </div>

        <div className="camera-stage">
          {enabled ? (
            <video
              ref={videoRef}
              autoPlay
              muted
              playsInline
              className={isLive ? "camera-preview live" : "camera-preview"}
            />
          ) : null}

          {!enabled ? (
            <div className="camera-overlay">
              <strong>Camera preview is paused.</strong>
              <p>Start the browser feed when you want an on-page view.</p>
            </div>
          ) : null}

          {enabled && !isLive ? (
            <div className="camera-overlay">
              <strong>{isStarting ? "Starting browser camera..." : "Camera preview unavailable."}</strong>
              <p>
                {error ||
                  "Allow camera permission in your browser to keep the preview visible here."}
              </p>
            </div>
          ) : null}
        </div>

        <div className="camera-preview-meta">
          <span className="detail-pill">
            {detectorOnline ? "Detector still running separately" : "Preview works independently of detector"}
          </span>
          <span className="detail-pill">
            {isLive ? "Laptop webcam active in browser" : "Browser preview not active"}
          </span>
        </div>

        <div className="incident-actions">
          <button className="primary-button inline-action" onClick={onToggle} type="button">
            {enabled ? "Stop Browser Preview" : "Start Browser Preview"}
          </button>
        </div>
      </article>
    </section>
  );
}

function App() {
  const [mode, setMode] = useState("login");
  const [view, setView] = useState("dashboard");
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) ?? "");
  const [user, setUser] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [selectedAlertId, setSelectedAlertId] = useState(null);
  const [settings, setSettings] = useState({
    emailEnabled: false,
    dashboardBaseUrl: "http://localhost:5173",
    fromEmail: "",
    registeredRecipients: 0,
    detectorOnline: false,
    detectorCameraId: "",
    lastHeartbeatAt: "",
    detectorAlertCooldownSeconds: null,
  });
  const [settingsForm, setSettingsForm] = useState({
    emailEnabled: false,
    dashboardBaseUrl: "http://localhost:5173",
  });
  const [faceEnrollment, setFaceEnrollment] = useState({
    enrolled: false,
    displayName: "",
    updatedAt: "",
  });
  const [selectedFaceFile, setSelectedFaceFile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [authLoading, setAuthLoading] = useState(false);
  const [settingsLoading, setSettingsLoading] = useState(false);
  const [faceLoading, setFaceLoading] = useState(false);
  const [statusLoading, setStatusLoading] = useState(false);
  const [authError, setAuthError] = useState("");
  const [dashboardError, setDashboardError] = useState("");
  const [backendOnline, setBackendOnline] = useState(true);
  const [streamConnected, setStreamConnected] = useState(false);
  const [systemArmed, setSystemArmed] = useState(true);
  const [cameraPreviewEnabled, setCameraPreviewEnabled] = useState(true);
  const [cameraPreviewState, setCameraPreviewState] = useState("idle");
  const [cameraPreviewError, setCameraPreviewError] = useState("");
  const [authForm, setAuthForm] = useState({
    fullName: "",
    email: "",
    password: "",
    deviceName: buildDeviceName(),
  });
  const browserVideoRef = useRef(null);
  const browserStreamRef = useRef(null);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      if (!token) {
        setLoading(false);
        return;
      }

      try {
        const [profile, nextSettings, nextFace] = await Promise.all([
          apiRequest(`${AUTH_URL}/me`, {}, token),
          apiRequest(SETTINGS_URL, {}, token),
          apiRequest(`${FACES_URL}/me`, {}, token),
        ]);

        if (active) {
          setUser(profile);
          setSettings(nextSettings);
          setSettingsForm({
            emailEnabled: nextSettings.emailEnabled,
            dashboardBaseUrl: nextSettings.dashboardBaseUrl,
          });
          setFaceEnrollment(nextFace);
          setBackendOnline(true);
        }
      } catch {
        if (active) {
          localStorage.removeItem(TOKEN_STORAGE_KEY);
          setToken("");
          setUser(null);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    bootstrap();

    return () => {
      active = false;
    };
  }, [token]);

  useEffect(() => {
    if (!token || !user) {
      return undefined;
    }

    let mounted = true;

    async function refreshData() {
      try {
        const [nextAlerts, nextSettings, nextFace] = await Promise.all([
          apiRequest(ALERTS_URL, {}, token),
          apiRequest(SETTINGS_URL, {}, token),
          apiRequest(`${FACES_URL}/me`, {}, token),
        ]);

        if (mounted) {
          setAlerts(nextAlerts ?? []);
          setSelectedAlertId((current) => current ?? nextAlerts?.[0]?.id ?? null);
          setSettings(nextSettings);
          setSettingsForm({
            emailEnabled: nextSettings.emailEnabled,
            dashboardBaseUrl: nextSettings.dashboardBaseUrl,
          });
          setFaceEnrollment(nextFace);
          setBackendOnline(true);
          setDashboardError("");
        }
      } catch (error) {
        if (mounted) {
          setBackendOnline(false);
          setDashboardError("Could not refresh data from the backend.");
          if (String(error.message).includes("401")) {
            localStorage.removeItem(TOKEN_STORAGE_KEY);
            setToken("");
            setUser(null);
          }
        }
      }
    }

    refreshData();
    const intervalId = setInterval(refreshData, 20000);

    return () => {
      mounted = false;
      clearInterval(intervalId);
    };
  }, [token, user]);

  useEffect(() => {
    function stopPreview() {
      if (browserStreamRef.current) {
        browserStreamRef.current.getTracks().forEach((track) => track.stop());
        browserStreamRef.current = null;
      }

      if (browserVideoRef.current) {
        browserVideoRef.current.srcObject = null;
      }
    }

    if (!user || view !== "dashboard" || !cameraPreviewEnabled) {
      stopPreview();
      setCameraPreviewState(cameraPreviewEnabled ? "idle" : "off");
      setCameraPreviewError("");
      return undefined;
    }

    let active = true;

    async function startPreview() {
      if (!navigator.mediaDevices?.getUserMedia) {
        setCameraPreviewState("error");
        setCameraPreviewError("This browser does not support live camera preview.");
        return;
      }

      setCameraPreviewState("starting");
      setCameraPreviewError("");

      try {
        const stream =
          browserStreamRef.current ??
          (await navigator.mediaDevices.getUserMedia({
            video: {
              facingMode: "user",
            },
            audio: false,
          }));

        if (!active) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        browserStreamRef.current = stream;

        if (browserVideoRef.current) {
          browserVideoRef.current.srcObject = stream;
          await browserVideoRef.current.play().catch(() => undefined);
        }

        setCameraPreviewState("live");
      } catch (error) {
        setCameraPreviewState("error");
        setCameraPreviewError(
          error?.message || "Camera access was blocked. Check browser permissions and webcam availability."
        );
      }
    }

    startPreview();

    return () => {
      active = false;
      stopPreview();
    };
  }, [cameraPreviewEnabled, user, view]);

  useEffect(() => {
    if (!token || !user) {
      return undefined;
    }

    const stream = new EventSource(
      `${ALERT_STREAM_URL}?access_token=${encodeURIComponent(token)}`
    );

    stream.addEventListener("connected", () => {
      setStreamConnected(true);
      setBackendOnline(true);
    });

    stream.addEventListener("alert", (event) => {
      const payload = JSON.parse(event.data);
      setStreamConnected(true);
      setBackendOnline(true);
      setDashboardError("");

      setAlerts((current) => {
        if (payload.type === "cleared") {
          return [];
        }

        if (!payload.alert) {
          return current;
        }

        return upsertAlert(current, payload.alert);
      });

      if (payload.type !== "cleared" && payload.alert?.id) {
        setSelectedAlertId((current) => current ?? payload.alert.id);
      } else if (payload.type === "cleared") {
        setSelectedAlertId(null);
      }
    });

    stream.onerror = () => {
      setStreamConnected(false);
      setDashboardError("Live stream disconnected. Falling back to periodic refresh.");
    };

    return () => {
      stream.close();
      setStreamConnected(false);
    };
  }, [token, user]);

  const selectedAlert =
    alerts.find((alert) => alert.id === selectedAlertId) ?? alerts[0] ?? null;
  const unresolvedAlerts = useMemo(
    () => alerts.filter((alert) => alert.status !== "RESOLVED").length,
    [alerts]
  );
  const acknowledgedAlerts = useMemo(
    () => alerts.filter((alert) => alert.status === "ACKNOWLEDGED").length,
    [alerts]
  );
  const intruderDetected =
    backendOnline && Boolean(selectedAlert) && selectedAlert.status !== "RESOLVED" && systemArmed;
  const appStateClass = !backendOnline
    ? "app backend-offline"
    : intruderDetected
      ? "app alert-state"
      : "app";

  function handleAuthFieldChange(event) {
    const { name, value } = event.target;
    setAuthForm((current) => ({ ...current, [name]: value }));
  }

  function handleSettingsFieldChange(event) {
    const { name, value, type, checked } = event.target;
    setSettingsForm((current) => ({
      ...current,
      [name]: type === "checkbox" ? checked : value,
    }));
  }

  function handleFaceFileChange(event) {
    setSelectedFaceFile(event.target.files?.[0] ?? null);
  }

  function toggleCameraPreview() {
    setCameraPreviewEnabled((current) => !current);
  }

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setAuthLoading(true);
    setAuthError("");

    try {
      const endpoint = mode === "signup" ? `${AUTH_URL}/signup` : `${AUTH_URL}/login`;
      const payload =
        mode === "signup"
          ? authForm
          : {
              email: authForm.email,
              password: authForm.password,
              deviceName: authForm.deviceName,
            };

      const response = await apiRequest(endpoint, {
        method: "POST",
        body: JSON.stringify(payload),
      });

      localStorage.setItem(TOKEN_STORAGE_KEY, response.token);
      setToken(response.token);
      setUser(response.user);
      setAuthForm((current) => ({
        ...current,
        password: "",
      }));
    } catch (error) {
      setAuthError(error.message);
    } finally {
      setAuthLoading(false);
    }
  }

  async function handleLogout() {
    try {
      await apiRequest(`${AUTH_URL}/logout`, { method: "POST" }, token);
    } catch {
      // Local logout should still succeed even if the backend token is already invalid.
      } finally {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        if (browserStreamRef.current) {
          browserStreamRef.current.getTracks().forEach((track) => track.stop());
          browserStreamRef.current = null;
        }
        if (browserVideoRef.current) {
          browserVideoRef.current.srcObject = null;
        }
        setToken("");
        setUser(null);
        setAlerts([]);
        setSelectedAlertId(null);
      }
  }

  async function clearAlerts() {
    await apiRequest(ALERTS_URL, { method: "DELETE" }, token);
    setAlerts([]);
    setSelectedAlertId(null);
  }

  async function updateAlertStatus(nextStatus) {
    if (!selectedAlert) {
      return;
    }

    setStatusLoading(true);
    setDashboardError("");

    try {
      const updatedAlert = await apiRequest(
        `${ALERTS_URL}/${selectedAlert.id}/status`,
        {
          method: "PATCH",
          body: JSON.stringify({ status: nextStatus }),
        },
        token
      );
      setAlerts((current) => upsertAlert(current, updatedAlert));
      setSelectedAlertId(updatedAlert.id);
    } catch (error) {
      setDashboardError(error.message);
    } finally {
      setStatusLoading(false);
    }
  }

  async function saveSettings(event) {
    event.preventDefault();
    setSettingsLoading(true);
    setDashboardError("");

    try {
      const nextSettings = await apiRequest(
        SETTINGS_URL,
        {
          method: "PATCH",
          body: JSON.stringify(settingsForm),
        },
        token
      );
      setSettings(nextSettings);
      setSettingsForm({
        emailEnabled: nextSettings.emailEnabled,
        dashboardBaseUrl: nextSettings.dashboardBaseUrl,
      });
    } catch (error) {
      setDashboardError(error.message);
    } finally {
      setSettingsLoading(false);
    }
  }

  async function uploadFace(event) {
    event.preventDefault();
    if (!selectedFaceFile) {
      setDashboardError("Choose a clear face image first.");
      return;
    }

    setFaceLoading(true);
    setDashboardError("");

    try {
      const formData = new FormData();
      formData.append("image", selectedFaceFile);

      const response = await fetch(`${FACES_URL}/me`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
        body: formData,
      });

      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || "Could not upload authorized face.");
      }

      const nextFace = await response.json();
      setFaceEnrollment(nextFace);
      setSelectedFaceFile(null);
    } catch (error) {
      setDashboardError(error.message);
    } finally {
      setFaceLoading(false);
    }
  }

  async function deleteFace() {
    setFaceLoading(true);
    setDashboardError("");

    try {
      await apiRequest(`${FACES_URL}/me`, { method: "DELETE" }, token);
      setFaceEnrollment({
        enrolled: false,
        displayName: "",
        updatedAt: "",
      });
      setSelectedFaceFile(null);
    } catch (error) {
      setDashboardError(error.message);
    } finally {
      setFaceLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="app">
        <div className="background-grid" />
        <div className="loading-screen">Preparing secure dashboard...</div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="app auth-app">
        <div className="background-grid" />
        <AuthPanel
          mode={mode}
          form={authForm}
          onChange={handleAuthFieldChange}
          onSubmit={handleAuthSubmit}
          loading={authLoading}
          error={authError}
          switchMode={setMode}
        />
      </div>
    );
  }

  return (
    <div className={appStateClass}>
      <div className="background-grid" />

      <header className="hero">
        <div>
          <p className="eyebrow">Protected Incident Console</p>
          <h1>Smart Intruder Alert System</h1>
          <p className="subtitle">
            Authenticated dashboard for incident review, live detector health,
            and product settings management.
          </p>
        </div>

        <div className="hero-actions">
          <div className="session-chip">
            <strong>{user.fullName}</strong>
            <span>{user.activeDeviceName}</span>
          </div>
          <div className={streamConnected ? "session-chip live-chip online" : "session-chip live-chip"}>
            <strong>{streamConnected ? "Live Feed On" : "Polling Mode"}</strong>
            <span>{streamConnected ? "SSE connected" : "Stream reconnecting"}</span>
          </div>
          <div className={settings.detectorOnline ? "session-chip live-chip online" : "session-chip live-chip"}>
            <strong>{settings.detectorOnline ? "Detector Online" : "Detector Offline"}</strong>
            <span>{settings.detectorCameraId || "No camera heartbeat"}</span>
          </div>
          <button
            className={view === "dashboard" ? "arm-button armed" : "arm-button"}
            onClick={() => setView("dashboard")}
          >
            Dashboard
          </button>
          <button
            className={view === "settings" ? "arm-button armed" : "arm-button"}
            onClick={() => setView("settings")}
          >
            Settings
          </button>
          <button className="secondary-button" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </header>

      {dashboardError ? <p className="global-note">{dashboardError}</p> : null}

      {view === "settings" ? (
        <SettingsPanel
          settings={settings}
          form={settingsForm}
          face={faceEnrollment}
          faceLoading={faceLoading}
          loading={settingsLoading}
          onChange={handleSettingsFieldChange}
          onSubmit={saveSettings}
          onFaceFileChange={handleFaceFileChange}
          onFaceUpload={uploadFace}
          onFaceDelete={deleteFace}
        />
      ) : (
        <main className="dashboard">
          <section className="status-card">
            <span className="status-dot" />
            <div>
              <h2>
                {!backendOnline
                  ? "Backend Offline"
                  : intruderDetected
                    ? "Active Incident"
                    : "System Secure"}
              </h2>
              <p>
                {!backendOnline
                  ? "The dashboard cannot reach the backend, so fresh incidents and status updates are paused."
                  : intruderDetected
                    ? "An incident needs review. You can acknowledge it, resolve it, or escalate it through notifications."
                    : "No unresolved intrusions are visible in the current monitoring window."}
              </p>
            </div>
          </section>

          <section className="metrics-grid">
            <article className="metric-card">
              <p>Total Incidents</p>
              <strong>{alerts.length}</strong>
            </article>
            <article className="metric-card">
              <p>Open Alerts</p>
              <strong>{unresolvedAlerts}</strong>
            </article>
            <article className="metric-card">
              <p>Acknowledged</p>
              <strong>{acknowledgedAlerts}</strong>
            </article>
            <article className="metric-card">
              <p>Last Incident</p>
              <strong>{formatTimestamp(selectedAlert?.timestamp)}</strong>
            </article>
          </section>

          <CameraPreviewPanel
            videoRef={browserVideoRef}
            enabled={cameraPreviewEnabled}
            state={cameraPreviewState}
            error={cameraPreviewError}
            detectorOnline={settings.detectorOnline}
            onToggle={toggleCameraPreview}
          />

          <section className="content-grid">
            <article className="panel spotlight-panel">
              <div className="panel-header">
                <h3>Incident Review</h3>
                <span>{selectedAlert?.severity ?? "STANDBY"}</span>
              </div>

              {selectedAlert ? (
                <>
                  <img
                    className="intruder-image"
                    src={
                      selectedAlert.imageBase64
                        ? `data:image/jpeg;base64,${selectedAlert.imageBase64}`
                        : buildAlertImageUrl(selectedAlert.id, token)
                    }
                    alt="Intruder snapshot"
                  />
                  <div className="snapshot-meta">
                    <p>{selectedAlert.message}</p>
                    <p>{formatTimestamp(selectedAlert.timestamp)}</p>
                  </div>
                  <div className="incident-meta">
                    <span className={`status-pill status-${selectedAlert.status.toLowerCase()}`}>
                      {selectedAlert.status}
                    </span>
                    <span className="detail-pill">Camera {selectedAlert.cameraId}</span>
                    <span className="detail-pill">Alert #{selectedAlert.id}</span>
                  </div>
                  <div className="incident-actions">
                    <button
                      className="secondary-button"
                      disabled={statusLoading || selectedAlert.status === "ACKNOWLEDGED"}
                      onClick={() => updateAlertStatus("ACKNOWLEDGED")}
                    >
                      {statusLoading ? "Updating..." : "Acknowledge"}
                    </button>
                    <button
                      className="primary-button inline-action"
                      disabled={statusLoading || selectedAlert.status === "RESOLVED"}
                      onClick={() => updateAlertStatus("RESOLVED")}
                    >
                      {statusLoading ? "Updating..." : "Resolve Incident"}
                    </button>
                    <button className="secondary-button" onClick={clearAlerts}>
                      Clear Alerts
                    </button>
                  </div>
                </>
              ) : (
                <div className="empty-state">Waiting for the first monitored incident.</div>
              )}
            </article>

            <article className="panel side-panel">
              <div className="panel-header">
                <h3>Operations Feed</h3>
                <span>{alerts.length} event(s)</span>
              </div>

              <div className="alert-list">
                {alerts.length === 0 ? (
                  <div className="empty-state compact">No alerts recorded yet.</div>
                ) : (
                  alerts.map((alert) => (
                    <button
                      className={selectedAlertId === alert.id ? "alert-item selected" : "alert-item"}
                      key={alert.id}
                      onClick={() => setSelectedAlertId(alert.id)}
                      type="button"
                    >
                      <div>
                        <strong>Alert #{alert.id}</strong>
                        <p>{alert.message}</p>
                      </div>
                      <div className="alert-meta">
                        <span className={`status-pill status-${alert.status.toLowerCase()}`}>
                          {alert.status}
                        </span>
                        <span>{formatTimestamp(alert.timestamp)}</span>
                      </div>
                    </button>
                  ))
                )}
              </div>
            </article>
          </section>
        </main>
      )}
    </div>
  );
}

export default App;
