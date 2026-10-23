"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

const API = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080/api/v1";

type ApiError = { message?: string; detail?: string; title?: string };

function errorMessage(body: ApiError, fallback: string) {
  return body.message || body.detail || body.title || fallback;
}

export default function SignupPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [workspaceName, setWorkspaceName] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [isError, setIsError] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;

    setBusy(true);
    setMessage("");
    setIsError(false);

    const normalizedEmail = email.trim().toLowerCase();
    try {
      const response = await fetch(`${API}/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: normalizedEmail,
          password,
          displayName: displayName.trim(),
          workspaceName: workspaceName.trim(),
        }),
      });
      const body = (await response.json().catch(() => ({}))) as ApiError;
      if (!response.ok) {
        throw new Error(errorMessage(body, response.status === 409
          ? "An account with this email already exists. Sign in instead."
          : `Could not create your account (HTTP ${response.status}).`));
      }

      // The local MVP uses HTTP Basic. Verify the new credentials before
      // navigating so a registration success cannot mask an auth/config issue.
      const basic = btoa(`${normalizedEmail}:${password}`);
      const authCheck = await fetch(`${API}/videos`, {
        headers: { Authorization: `Basic ${basic}` },
      });
      if (!authCheck.ok) {
        throw new Error("Your account was created, but sign-in verification failed. Please go to the dashboard and sign in with your new credentials.");
      }

      sessionStorage.setItem("vc-basic", basic);
      setMessage("Workspace created. Opening your video library…");
      router.replace("/dashboard");
    } catch (error) {
      setIsError(true);
      setMessage(error instanceof Error
        ? error.message
        : "Could not connect to VideoCloud. Check that the local API is running.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell subPage">
      <Link className="brand" href="/">VideoCloud<span>●</span></Link>
      <section className="signupCard">
        <p className="eyebrow">YOUR WORKSPACE STARTS HERE</p>
        <h1>Create your account</h1>
        <p className="heroCopy">Create a local workspace to upload, manage and share your videos.</p>
        <form onSubmit={submit}>
          <label htmlFor="displayName">Your name</label>
          <input id="displayName" name="displayName" autoComplete="name" value={displayName}
            onChange={(event) => setDisplayName(event.target.value)} minLength={2} maxLength={160} required />
          <label htmlFor="email">Work email</label>
          <input id="email" name="email" type="email" autoComplete="email" placeholder="you@company.com"
            value={email} onChange={(event) => setEmail(event.target.value)} maxLength={320} required />
          <label htmlFor="workspaceName">Workspace name</label>
          <input id="workspaceName" name="workspaceName" autoComplete="organization" placeholder="Acme Studio"
            value={workspaceName} onChange={(event) => setWorkspaceName(event.target.value)}
            minLength={2} maxLength={160} required />
          <label htmlFor="password">Password</label>
          <input id="password" name="password" type="password" autoComplete="new-password"
            value={password} onChange={(event) => setPassword(event.target.value)}
            minLength={12} maxLength={72} aria-describedby="password-help" required />
          <p id="password-help" className="disclaimer">Use at least 12 characters. This local MVP uses HTTP Basic authentication and is not suitable for public production deployment.</p>
          <button className="button" type="submit" disabled={busy}>
            {busy ? "Creating workspace…" : "Create workspace"}
          </button>
        </form>
        {message && <p role={isError ? "alert" : "status"}>{message}</p>}
        <p className="disclaimer">Already have an account? <Link href="/dashboard">Sign in</Link></p>
      </section>
    </main>
  );
}
