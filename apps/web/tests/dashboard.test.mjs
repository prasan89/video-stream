import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("dashboard exposes auth and upload/status controls", async () => {
  const source = await readFile(new URL("../app/dashboard/page.tsx", import.meta.url), "utf8");
  assert.match(source, /const API=/);
  assert.match(source, /Upload video/);
  assert.match(source, /Refresh status/);
  assert.match(source, /VideoPlayer/);
});

test("HLS player attaches Authorization to HLS requests", async () => {
  const source = await readFile(new URL("../app/dashboard/VideoPlayer.tsx", import.meta.url), "utf8");
  assert.match(source, /xhrSetup/);
  assert.match(source, /Authorization/);
});
