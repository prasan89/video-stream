import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("pricing page labels prices as INR and discloses placeholder status", async () => {
  const source = await readFile(new URL("../app/pricing/page.tsx", import.meta.url), "utf8");
  assert.match(source, /₹499/);
  assert.match(source, /₹1,499/);
  assert.match(source, /No payment is collected by this demo/);
});
