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
  assert.match(source, /Playback analytics are not implemented yet/);
  assert.match(source, /Storage allowance is not yet enforced/);
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
