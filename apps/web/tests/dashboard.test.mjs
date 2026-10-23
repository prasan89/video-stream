import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("creator workspace exposes authenticated library, upload, refresh and playback", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /const API\s*=/);
  assert.match(source, /Upload video/);
  assert.match(source, /void load\(\)/);
  assert.match(source, /VideoPlayer/);
  assert.match(source, /\$\{API\}\/usage/);
});

test("workspace storage display uses real usage API values without invented analytics", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /storedBytes/);
  assert.match(source, /Storage usage unavailable/);
  assert.match(source, /Playback starts/);
  assert.match(source, /Unique viewer sessions/);
  assert.match(source, /Top videos/);
  // plan quota is now real; the old placeholder is gone
  assert.match(source, /storageLimitBytes/);
  assert.match(source, /videoLimit/);
  assert.match(source, /planCode/);
});

test("dashboard uses resumable chunked upload with SHA-256 checksums", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /uploads\/resumable/);
  assert.match(source, /X-Chunk-SHA256/);
  assert.match(source, /chunkSizeBytes/);
  assert.match(source, /chunkCount/);
  assert.match(source, /uploads\/resumable\/\$\{session\.uploadId\}\/complete/);
  // browser-refresh resume: session persisted in localStorage and chunks queried from server
  assert.match(source, /vc-pending-upload/);
  assert.match(source, /localStorage\.setItem/);
  assert.match(source, /localStorage\.removeItem/);
  assert.match(source, /uploaded\.has\(index\)/);
});

test("HLS player attaches Authorization to HLS requests", async () => {
  const source = await readFile(new URL("../app/dashboard/VideoPlayer.tsx", import.meta.url), "utf8");
  assert.match(source, /xhrSetup/);
  assert.match(source, /Authorization/);
});

test("dashboard offers actual navigation for library, analytics, billing and pricing", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /Watch \/ Library/);
  assert.match(source, /Analytics/);
  assert.match(source, /\/billing/);
  assert.match(source, /\/pricing/);
});

test("sidebar uses dedicated compact icon and label elements", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /className="vc-nav-icon"/);
  assert.match(source, /className="vc-nav-label"/);
  assert.match(source, /Watch \/ Library/);
});

test("processing videos trigger periodic refreshes for status and storage", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /setInterval\(\(\) => \{ void load\(\); \}, 4000\)/);
  assert.match(source, /video\.status === "PROCESSING"/);
});

test("analytics view loads real playback metrics and renders a daily chart", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /fetch\(\`\$\{API\}\/analytics/);
  assert.match(source, /analytics\.daily\.map/);
  assert.match(source, /analytics\.videos\.map/);
  assert.match(source, /analytics\.summary\.watch_seconds/);
});

test("video player sends playback events to the authenticated analytics API", async () => {
  const source = await readFile(new URL("../app/dashboard/VideoPlayer.tsx", import.meta.url), "utf8");
  assert.match(source, /\/analytics\/videos\/\$\{videoId\}\/events/);
  assert.match(source, /sendEvent\("PLAY"\)/);
  assert.match(source, /sendEvent\("COMPLETE"\)/);
  assert.match(source, /watchedSeconds/);
});
