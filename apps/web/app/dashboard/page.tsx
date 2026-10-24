"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import VideoPlayer from "./VideoPlayer";

type Video = {
  id: string; title: string; description: string | null; visibility: string; folderName: string | null;
  status: string; durationSeconds: number | null; createdAt: string;
};
type Workspace = { id: string; name: string; slug: string; role: string };
type Usage = { videoCount: number; storedBytes: number; reservedUploadBytes?: number; storageLimitBytes?: number; availableBytes?: number; videoLimit?: number; planCode?: string; billingMode: string; note?: string };
type Analytics = { periodDays: number; summary: { plays: number; completions: number; unique_viewers: number; watch_seconds: number }; daily: { day: string; plays: number; unique_viewers: number; watch_seconds: number }[]; videos: { id: string; title: string; plays: number; unique_viewers: number; watch_seconds: number }[]; note: string };
const API = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080/api/v1";
const formatBytes = (bytes: number) => {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index > 1 ? 1 : 0)} ${units[index]}`;
};

function Brand() { return <Link className="brand" href="/">VideoCloud<span>●</span></Link>; }

export default function DashboardPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [workspaceName, setWorkspaceName] = useState("My Studio");
  const [auth, setAuth] = useState("");
  const [authReady, setAuthReady] = useState(false);
  const [workspaceId, setWorkspaceId] = useState("");
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [videos, setVideos] = useState<Video[]>([]);
  const [usage, setUsage] = useState<Usage | null>(null);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [analyticsError, setAnalyticsError] = useState("");
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [message, setMessage] = useState("");
  const [messageError, setMessageError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [showUpload, setShowUpload] = useState(false);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("newest");\n  const [folderFilter, setFolderFilter] = useState("all");
  const [view, setView] = useState<"library" | "analytics">("library");
  const [shareLinks, setShareLinks] = useState<Record<string, string>>({});

  const headers = useCallback((json = false): HeadersInit => ({
    ...(auth ? { Authorization: `Basic ${auth}` } : {}),
    ...(workspaceId ? { "X-Workspace-Id": workspaceId } : {}),
    ...(json ? { "Content-Type": "application/json" } : {}),
  }), [auth, workspaceId]);

  const load = useCallback(async () => {
    if (!auth || !workspaceId) return;
    try {
      const [videoResponse, usageResponse] = await Promise.all([
        fetch(`${API}/videos`, { headers: headers() }),
        fetch(`${API}/usage`, { headers: headers() }),
      ]);
      if (videoResponse.status === 401 || videoResponse.status === 403) {
        sessionStorage.removeItem("vc-basic"); setAuth(""); setVideos([]);
        setMessage("Your session is no longer valid. Please sign in again."); setMessageError(true); return;
      }
      if (!videoResponse.ok) throw new Error(`Could not load your library (HTTP ${videoResponse.status}).`);
      setVideos(await videoResponse.json());
      if (usageResponse.ok) setUsage(await usageResponse.json());
      else setUsage(null);
      setMessageError(false);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not connect to the local API.");
      setMessageError(true);
    }
  }, [auth, headers, workspaceId]);

  useEffect(() => {
    const saved = sessionStorage.getItem("vc-basic");
    if (saved) setAuth(saved);
    setAuthReady(true);
  }, []);
  useEffect(() => {
    if (!auth) { setWorkspaces([]); setWorkspaceId(""); return; }
    sessionStorage.setItem("vc-basic", auth);
    let cancelled = false;
    fetch(`${API}/workspaces`, { headers: { Authorization: `Basic ${auth}` } })
      .then(async response => {
        if (!response.ok) throw new Error(`Could not load workspaces (HTTP ${response.status}).`);
        return await response.json() as Workspace[];
      })
      .then(items => {
        if (cancelled) return;
        setWorkspaces(items);
        const saved = localStorage.getItem("vc-workspace-id");
        const selected = items.find(item => item.id === saved) || (items.length === 1 ? items[0] : null);
        if (selected) {
          setWorkspaceId(selected.id);
          localStorage.setItem("vc-workspace-id", selected.id);
        } else {
          setWorkspaceId("");
          setVideos([]); setUsage(null);
          setMessage(items.length ? "Select a workspace to view its videos and storage." : "No workspace membership found.");
          setMessageError(items.length === 0);
        }
      })
      .catch(error => { if (!cancelled) { setMessage(error instanceof Error ? error.message : "Could not load workspaces."); setMessageError(true); } });
    return () => { cancelled = true; };
  }, [auth]);
  useEffect(() => { if (auth && workspaceId) void load(); }, [auth, workspaceId, load]);
  useEffect(() => {
    if (!auth || !workspaceId || view !== "analytics") return;
    let cancelled = false;
    fetch(`${API}/analytics`, { headers: headers() }).then(async response => {
      if (!response.ok) throw new Error(`Analytics request failed (HTTP ${response.status}).`);
      return await response.json() as Analytics;
    }).then(data => { if (!cancelled) { setAnalytics(data); setAnalyticsError(""); } })
      .catch(error => { if (!cancelled) setAnalyticsError(error instanceof Error ? error.message : "Could not load analytics."); });
    return () => { cancelled = true; };
  }, [auth, workspaceId, view, headers]);
  // Keep processing status and storage figures current while FFmpeg runs.
  useEffect(() => {
    if (!auth || !workspaceId || !videos.some(video => video.status === "PROCESSING" || video.status === "UPLOADED" || video.status === "QUEUED")) return;
    const timer = window.setInterval(() => { void load(); }, 4000);
    return () => window.clearInterval(timer);
  }, [auth, workspaceId, videos, load]);

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return; setBusy(true); setMessage("");
    try {
      const normalizedEmail = email.trim().toLowerCase();
      const response = await fetch(`${API}/auth/register`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: normalizedEmail, password, displayName: displayName.trim(), workspaceName: workspaceName.trim() }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || (response.status === 409 ? "This email already has an account. Please sign in." : `Registration failed (HTTP ${response.status}).`));
      const basic = btoa(`${normalizedEmail}:${password}`);
      const check = await fetch(`${API}/workspaces`, { headers: { Authorization: `Basic ${basic}` } });
      if (!check.ok) throw new Error("Account created, but sign-in verification failed. Please sign in.");
      sessionStorage.setItem("vc-basic", basic); setAuth(basic); setWorkspaces(await check.json());
      setMessage("Workspace created successfully."); setMessageError(false);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Registration failed."); setMessageError(true); }
    finally { setBusy(false); }
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return; setBusy(true); setMessage("");
    try {
      const basic = btoa(`${email.trim().toLowerCase()}:${password}`);
      const response = await fetch(`${API}/workspaces`, { headers: { Authorization: `Basic ${basic}` } });
      if (!response.ok) throw new Error(response.status === 401 ? "Email or password is incorrect." : `Sign-in failed (HTTP ${response.status}).`);
      const items = await response.json() as Workspace[]; sessionStorage.setItem("vc-basic", basic);
      setAuth(basic); setWorkspaces(items); setMessage(items.length > 1 ? "Signed in. Select a workspace." : "Signed in."); setMessageError(false);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Sign-in failed."); setMessageError(true); }
    finally { setBusy(false); }
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!file || !title.trim() || busy) return;
    setBusy(true); setUploadProgress(0); setMessage("Preparing resumable upload…"); setMessageError(false);
    const pendingKey = "vc-pending-upload";
    try {
      const sha256 = async (blob: Blob) => {
        const digest = await crypto.subtle.digest("SHA-256", await blob.arrayBuffer());
        return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
      };
      const fileSha = await sha256(file);
      type PendingUpload = { uploadId: string; fileName: string; sizeBytes: number; sha256: string; title: string; chunkSizeBytes: number; chunkCount: number };
      let session: PendingUpload | null = null;
      let uploaded = new Set<number>();
      const saved = localStorage.getItem(pendingKey);
      if (saved) {
        try {
          const candidate = JSON.parse(saved) as PendingUpload;
          if (candidate.fileName === file.name && candidate.sizeBytes === file.size &&
              candidate.sha256 === fileSha && candidate.title === title.trim()) {
            const statusResponse = await fetch(`${API}/uploads/resumable/${candidate.uploadId}`, { headers: headers() });
            if (statusResponse.ok) {
              const status = await statusResponse.json();
              if (["CREATED", "UPLOADING"].includes(status.status)) {
                session = candidate;
                uploaded = new Set<number>((status.chunks || []).map((chunk: { chunk_index: number }) => Number(chunk.chunk_index)));
                setMessage(`Resuming upload… ${uploaded.size} of ${candidate.chunkCount} chunks already received.`);
              }
            }
          }
        } catch { /* A stale or malformed local session is ignored. */ }
      }
      if (!session) {
        const start = await fetch(`${API}/uploads/resumable`, {
          method: "POST", headers: headers(true),
          body: JSON.stringify({ title: title.trim(), sizeBytes: file.size, contentType: file.type || "video/mp4", filename: file.name, sha256: fileSha })
        });
        const data = await start.json().catch(() => ({}));
        if (!start.ok) throw new Error(data.message || `Could not start upload (HTTP ${start.status}).`);
        session = { uploadId: data.uploadId, fileName: file.name, sizeBytes: file.size, sha256: fileSha, title: title.trim(), chunkSizeBytes: Number(data.chunkSizeBytes), chunkCount: Number(data.chunkCount) };
        localStorage.setItem(pendingKey, JSON.stringify(session));
      }
      const chunkSize = session.chunkSizeBytes;
      const chunks = session.chunkCount;
      for (let index = 0; index < chunks; index++) {
        if (uploaded.has(index)) continue;
        const chunk = file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize));
        const chunkSha = await sha256(chunk);
        let lastError: Error | null = null;
        for (let attempt = 0; attempt < 3; attempt++) {
          try {
            const response = await fetch(`${API}/uploads/resumable/${session.uploadId}/chunks/${index}`, {
              method: "PUT", headers: { ...headers(), "Content-Type": "application/octet-stream", "X-Chunk-SHA256": chunkSha }, body: chunk
            });
            if (!response.ok) {
              const data = await response.json().catch(() => ({}));
              if (response.status < 500 && response.status !== 429) throw new Error(data.message || `Chunk ${index + 1} failed (HTTP ${response.status}).`);
              throw new Error(data.message || `Temporary upload error (HTTP ${response.status}).`);
            }
            lastError = null; break;
          } catch (error) {
            lastError = error instanceof Error ? error : new Error("Chunk upload failed");
            if (attempt < 2) await new Promise(resolve => window.setTimeout(resolve, 400 * (attempt + 1)));
          }
        }
        if (lastError) throw new Error(`Chunk ${index + 1}/${chunks} could not be uploaded. Re-select the same file to resume this session: ${lastError.message}`);
        uploaded.add(index);
        setUploadProgress(Math.round((uploaded.size / chunks) * 100));
        setMessage(`Uploading video… ${Math.round((uploaded.size / chunks) * 100)}%`);
      }
      const complete = await fetch(`${API}/uploads/resumable/${session.uploadId}/complete`, { method: "POST", headers: headers() });
      const result = await complete.json().catch(() => ({}));
      if (!complete.ok) throw new Error(result.message || `Could not complete upload (HTTP ${complete.status}).`);
      localStorage.removeItem(pendingKey);
      setMessage("Upload verified. Video processing has started."); setTitle(""); setFile(null); setShowUpload(false); setUploadProgress(0);
      await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Upload failed."); setMessageError(true); }
    finally { setBusy(false); }
  }

  async function updateVideo(video: Video, updates: { visibility?: string; folderName?: string | null }) {
    try {
      const response = await fetch(`${API}/videos/${video.id}`, {
        method: "PATCH", headers: headers(true),
        body: JSON.stringify({
          title: video.title, description: video.description || "",
          visibility: updates.visibility ?? video.visibility,
          folderName: updates.folderName === undefined ? video.folderName : updates.folderName
        })
      });
      if (!response.ok) throw new Error(`Could not update video (HTTP ${response.status}).`);
      setMessage("Video updated."); setMessageError(false); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not update video."); setMessageError(true); }
  }

  async function remove(video: Video) {
    if (!window.confirm(`Delete “${video.title}” and its local media?`)) return;
    try {
      const response = await fetch(`${API}/videos/${video.id}`, { method: "DELETE", headers: headers() });
      if (!response.ok) throw new Error(`Delete failed (HTTP ${response.status}).`);
      setMessage("Video deleted."); setMessageError(false); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Delete failed."); setMessageError(true); }
  }

  async function createShare(video: Video) {
    try {
      const response = await fetch(`${API}/videos/${video.id}/shares`, { method: "POST", headers: headers() });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Could not create share link.");
      const url = `${window.location.origin}/share/${data.token}`;
      setShareLinks(previous => ({ ...previous, [video.id]: url }));
      try { await navigator.clipboard.writeText(url); setMessage("Share link created and copied."); }
      catch { setMessage("Share link created. Copy the link shown on the video card."); }
      setMessageError(false);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Share link failed."); setMessageError(true); }
  }

  const folders = useMemo(() => Array.from(new Set(videos.map(video => video.folderName).filter((folder): folder is string => Boolean(folder)))).sort((a,b) => a.localeCompare(b)), [videos]);\n  const filteredVideos = useMemo(() => videos.filter(video => video.title.toLowerCase().includes(search.toLowerCase()) && (folderFilter === "all" || (folderFilter === "__uncategorized__" ? !video.folderName : video.folderName === folderFilter)))
    .sort((a, b) => sort === "title" ? a.title.localeCompare(b.title) : sort === "oldest" ? a.createdAt.localeCompare(b.createdAt) : b.createdAt.localeCompare(a.createdAt)), [videos, search, sort, folderFilter]);
  const storedBytes = usage?.storedBytes ?? 0;
  const storageLimit = 2 * 1024 ** 3;
  const effectiveLimit = usage?.storageLimitBytes ?? storageLimit;
  const storagePercent = Math.min(100, ((storedBytes + (usage?.reservedUploadBytes ?? 0)) / Math.max(1,effectiveLimit)) * 100);

  if (!authReady) return <main className="workspace-loading">Loading your workspace…</main>;

  if (!auth) return (
    <main className="auth-screen">
      <header className="auth-header"><Brand /><Link href="/">Back to homepage</Link></header>
      <section className="auth-card">
        <p className="eyebrow">YOUR CREATOR WORKSPACE</p><h1>Welcome to VideoCloud</h1>
        <p className="heroCopy">Sign in or create a workspace to manage your video library.</p>
        <form onSubmit={login} className="workspace-form">
          <h2>Sign in</h2><label htmlFor="login-email">Work email</label>
          <input id="login-email" type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} required />
          <label htmlFor="login-password">Password</label>
          <input id="login-password" type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required />
          <button className="vc-primary" disabled={busy}>{busy ? "Please wait…" : "Sign in"}</button>
        </form>
        <details className="create-account"><summary>Create a new account</summary>
          <form onSubmit={register} className="workspace-form">
            <label htmlFor="signup-name">Your name</label><input id="signup-name" value={displayName} onChange={event => setDisplayName(event.target.value)} minLength={2} required />
            <label htmlFor="signup-workspace">Workspace name</label><input id="signup-workspace" value={workspaceName} onChange={event => setWorkspaceName(event.target.value)} minLength={2} required />
            <label htmlFor="signup-email">Work email</label><input id="signup-email" type="email" value={email} onChange={event => setEmail(event.target.value)} required />
            <label htmlFor="signup-password">Password (at least 12 characters)</label><input id="signup-password" type="password" minLength={12} maxLength={72} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} required />
            <button className="vc-primary" disabled={busy}>{busy ? "Creating…" : "Create workspace"}</button>
          </form>
        </details>
        {message && <p className={messageError ? "vc-alert" : "vc-notice"} role={messageError ? "alert" : "status"}>{message}</p>}
        <p className="vc-disclaimer">Local development authentication only. Do not expose this build publicly.</p>
      </section>
    </main>
  );

  return (
    <main className="vc-app">
      <aside className="vc-sidebar">
        <Brand />
        <nav className="vc-side-nav" aria-label="Main navigation">
          <button className={view === "library" ? "vc-side-link active" : "vc-side-link"} onClick={() => setView("library")}>⌂ <span>Home</span></button>
          <button className="vc-side-link" onClick={() => { setSearch(""); setView("library"); }}><span className="vc-nav-icon">▷</span><span className="vc-nav-label">Watch / Library</span></button>
          <button className={view === "analytics" ? "vc-side-link active" : "vc-side-link"} onClick={() => setView("analytics")}><span className="vc-nav-icon">▥</span><span className="vc-nav-label">Analytics</span></button>
          <Link className="vc-side-link" href="/billing"><span className="vc-nav-icon">◇</span><span className="vc-nav-label">Plans &amp; billing</span></Link>
          <Link className="vc-side-link" href="/pricing"><span className="vc-nav-icon">＋</span><span className="vc-nav-label">Pricing</span></Link>
        </nav>
        <div className="vc-storage-card">
          <div className="vc-storage-heading"><strong>Storage</strong><span>LOCAL</span></div>
          <p>{formatBytes(storedBytes + (usage?.reservedUploadBytes ?? 0))} of {formatBytes(usage?.storageLimitBytes ?? 0)} used</p><div className="vc-progress"><span style={{ width: `${storagePercent}%` }} /></div>
          <small>{usage ? "Usage from your original video files" : "Storage usage unavailable"}</small>
          <p className="vc-storage-foot">{usage?.planCode ?? "Plan unavailable"} · {usage?.videoCount ?? videos.length}/{usage?.videoLimit ?? "—"} videos</p>
        </div>
        <button className="vc-side-link vc-logout" onClick={() => { sessionStorage.removeItem("vc-basic"); setAuth(""); setVideos([]); setUsage(null); }}><span className="vc-nav-icon">↪</span><span className="vc-nav-label">Sign out</span></button>
      </aside>

      <section className="vc-main">
        <header className="vc-topbar">
          <div className="vc-search"><span>⌕</span><input aria-label="Search library" placeholder="Search your library" value={search} onChange={event => setSearch(event.target.value)} /></div>
          <div className="vc-top-actions">{workspaces.length > 1 && <label className="vc-workspace-picker">Workspace <select aria-label="Active workspace" value={workspaceId} onChange={event => { const id = event.target.value; setWorkspaceId(id); localStorage.setItem("vc-workspace-id", id); setVideos([]); setUsage(null); setAnalytics(null); setShareLinks({}); setMessage("Loading selected workspace…"); setMessageError(false); }}><option value="">Select workspace</option>{workspaces.map(workspace => <option key={workspace.id} value={workspace.id}>{workspace.name} ({workspace.role})</option>)}</select></label>}<Link href="/pricing">Plans</Link><button className="vc-primary" onClick={() => { setShowUpload(value => !value); setView("library"); }}>＋ Create / Upload</button><div className="vc-avatar" title="Signed in">VC</div></div>
        </header>

        <div className="vc-quick-actions">
          <button className="vc-quick-card" onClick={() => { setShowUpload(true); setView("library"); }}><span className="vc-quick-icon">⇧</span><span><strong>Upload</strong><small>From your computer</small></span></button>
          <button className="vc-quick-card" onClick={() => { setShowUpload(true); setView("library"); }}><span className="vc-quick-icon">▣</span><span><strong>Add to library</strong><small>Upload a video file</small></span></button>
          <button className="vc-quick-card" onClick={() => { setShowUpload(true); setView("library"); }}><span className="vc-quick-icon">✎</span><span><strong>Create</strong><small>Start with a video upload</small></span></button>
          <button className="vc-quick-card" disabled title="Screen/webcam recording is not implemented yet"><span className="vc-quick-icon">◉</span><span><strong>Record</strong><small>Coming in a later phase</small></span></button>
          <button className="vc-quick-card" disabled title="Live events are not implemented yet"><span className="vc-quick-icon">▻</span><span><strong>Host</strong><small>Live events later</small></span></button>
        </div>

        {message && <p className={messageError ? "vc-alert vc-message" : "vc-notice vc-message"} role={messageError ? "alert" : "status"}>{message}</p>}

        {showUpload && view === "library" && <section className="vc-upload-panel">
          <div><p className="eyebrow">ADD TO YOUR LIBRARY</p><h2>Upload a video</h2><p>Choose a video file. FFmpeg will prepare it for HLS playback in the background.</p></div>
          <form onSubmit={upload} className="vc-upload-form">
            <label htmlFor="video-title">Video title</label><input id="video-title" value={title} onChange={event => setTitle(event.target.value)} maxLength={250} required />
            <label htmlFor="video-file">Video file</label>
            <div className={file ? "vc-file-dropzone has-file" : "vc-file-dropzone"}>
              <input className="vc-file-input" id="video-file" type="file" accept="video/mp4,video/quicktime,video/webm,video/x-matroska" onChange={event => setFile(event.target.files?.[0] || null)} required aria-describedby="video-file-help" />
              <span className="vc-file-icon" aria-hidden="true">{file ? "✓" : "↑"}</span>
              <div className="vc-file-copy">
                <strong>{file ? file.name : "Choose a video to upload"}</strong>
                <span>{file ? `${formatBytes(file.size)} · Ready to upload` : "Click to browse your device"}</span>
              </div>
              <span className="vc-file-browse">{file ? "Change file" : "Browse files"}</span>
            </div>
            <p className="vc-file-help" id="video-file-help">MP4, MOV, WebM or MKV · SHA-256 verification and retry enabled</p>
            {busy && <div className="vc-upload-progress" role="status"><div className="vc-progress"><span style={{ width: `${uploadProgress}%` }} /></div><small>{uploadProgress}% uploaded</small></div>}
            <div className="vc-upload-actions"><button type="button" className="vc-secondary" onClick={() => setShowUpload(false)}>Cancel</button><button className="vc-primary" disabled={busy || !file}>{busy ? "Uploading…" : "Upload video"}</button></div>
          </form>
        </section>}

        {view === "analytics" ? <section className="vc-section">
          <p className="eyebrow">WORKSPACE INSIGHTS · LAST 30 DAYS</p><h1>Analytics</h1>
          <div className="vc-metric-grid">
            <article className="vc-metric"><span>Playback starts</span><strong>{analytics?.summary.plays ?? "—"}</strong></article>
            <article className="vc-metric"><span>Unique viewer sessions</span><strong>{analytics?.summary.unique_viewers ?? "—"}</strong></article>
            <article className="vc-metric"><span>Watch time</span><strong>{analytics ? `${Math.floor(analytics.summary.watch_seconds / 60)} min` : "—"}</strong><small>{analytics ? `${analytics.summary.completions} completed plays` : "Loading recorded events"}</small></article>
          </div>
          {analyticsError && <p className="vc-alert" role="alert">{analyticsError}</p>}
          {!analytics && !analyticsError && <p role="status">Loading playback analytics…</p>}
          {analytics && <><div className="vc-analytics-panel"><h2>Daily playback starts</h2>{analytics.daily.length === 0 ? <p>No playback events yet. Play a video from your signed-in creator dashboard to start collecting analytics.</p> : <div className="vc-daily-bars">{analytics.daily.map(day => <div className="vc-daily-bar" key={day.day} title={`${day.day}: ${day.plays} starts`}><div><span style={{height:`${Math.max(4, day.plays / Math.max(1,...analytics.daily.map(item=>item.plays))*100)}%`}} /></div><small>{String(day.day).slice(5)}</small><strong>{day.plays}</strong></div>)}</div>}</div>
          <div className="vc-analytics-panel"><h2>Top videos</h2>{analytics.videos.length === 0 ? <p>No videos in this workspace yet.</p> : <div className="vc-analytics-table"><div className="vc-analytics-row header"><span>Video</span><span>Starts</span><span>Unique sessions</span><span>Watch time</span></div>{analytics.videos.map(video => <div className="vc-analytics-row" key={video.id}><span>{video.title}</span><span>{video.plays}</span><span>{video.unique_viewers}</span><span>{Math.floor(video.watch_seconds/60)} min</span></div>)}</div>}</div>
          <p className="vc-analytics-note">{analytics.note}</p></>}
        </section> : <section className="vc-section">
          <div className="vc-section-heading"><div><p className="eyebrow">YOUR CONTENT</p><h1>Video library</h1><p className="vc-subtitle">Manage, preview and share videos from your workspace.</p></div><div className="vc-library-controls"><select aria-label="Filter by folder" value={folderFilter} onChange={event => setFolderFilter(event.target.value)}><option value="all">All folders</option><option value="__uncategorized__">Uncategorized</option>{folders.map(folder => <option key={folder} value={folder}>{folder}</option>)}</select><select aria-label="Sort videos" value={sort} onChange={event => setSort(event.target.value)}><option value="newest">Recently added</option><option value="oldest">Oldest first</option><option value="title">Title A–Z</option></select><button className="vc-secondary" onClick={() => void load()}>↻ Refresh</button></div></div>
          <div className="vc-library-summary"><span>{filteredVideos.length} {filteredVideos.length === 1 ? "video" : "videos"}</span><span>{formatBytes(storedBytes)} stored</span></div>
          {filteredVideos.length === 0 ? <div className="vc-empty-state"><div className="vc-empty-icon">⇧</div><h2>{search ? "No matching videos" : "Your video library starts here"}</h2><p>{search ? "Try another search term." : "Upload your first video to start building a branded, shareable library."}</p>{!search && <button className="vc-primary" onClick={() => setShowUpload(true)}>＋ Upload your first video</button>}</div> :
            <div className="vc-video-grid">{filteredVideos.map(video => <article className="vc-video-card" key={video.id}>
              <div className="vc-video-preview">{video.status === "READY" ? <VideoPlayer videoId={video.id} auth={auth} workspaceId={workspaceId} /> : <div className="vc-processing"><span>{video.status === "FAILED" ? "!" : "◷"}</span><strong>{video.status === "FAILED" ? "Processing failed" : "Preparing your video"}</strong><small>{video.status}</small></div>}</div>
              <div className="vc-video-details"><div className="vc-video-title-row"><h2 title={video.title}>{video.title}</h2><span className={video.status === "READY" ? "vc-status ready" : video.status === "FAILED" ? "vc-status failed" : "vc-status"}>{video.status}</span></div>
                <p className="vc-video-meta">{new Date(video.createdAt).toLocaleDateString()} · {video.visibility}</p>
                <div className="vc-video-actions"><label className="vc-visibility-label" htmlFor={`visibility-${video.id}`}>Access</label><select id={`visibility-${video.id}`} value={video.visibility} onChange={event => void updateVideo(video, { visibility: event.target.value })}><option>PRIVATE</option><option>UNLISTED</option><option>PUBLIC</option></select><label className="vc-visibility-label" htmlFor={`folder-${video.id}`}>Folder</label><select id={`folder-${video.id}`} aria-label={`Folder for ${video.title}`} value={video.folderName || ""} onChange={event => { const value = event.target.value; if (value === "__new__") { const name = window.prompt("Enter a folder name (max 80 characters):"); if (name?.trim()) void updateVideo(video, { folderName: name.trim() }); } else void updateVideo(video, { folderName: value || null }); }}><option value="">Uncategorized</option>{folders.map(folder => <option key={folder} value={folder}>{folder}</option>)}<option value="__new__">+ New folder…</option></select><button className="vc-icon-button" title="Create share link" onClick={() => void createShare(video)}>↗ Share</button><button className="vc-icon-button danger" title="Delete video" onClick={() => void remove(video)}>⌫</button></div>
                {shareLinks[video.id] && <div className="vc-share-url"><input aria-label="Share URL" readOnly value={shareLinks[video.id]} onFocus={event => event.currentTarget.select()} /><button onClick={() => void navigator.clipboard?.writeText(shareLinks[video.id])}>Copy</button><Link href={shareLinks[video.id]}>Open</Link></div>}
              </div>
            </article>)}</div>}
        </section>}
        <footer className="vc-footer"><Brand /><span>Local MVP · Storage usage reflects original files only · Payments and analytics are not enabled</span></footer>
      </section>
    </main>
  );
}
