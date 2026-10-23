import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("signup page submits the real registration API contract", async () => {
  const source = await readFile(new URL("../app/signup/page.tsx", import.meta.url), "utf8");
  assert.match(source, /fetch\(\`\$\{API\}\/auth\/register\`/);
  assert.match(source, /displayName/);
  assert.match(source, /workspaceName/);
  assert.match(source, /minLength=\{12\}/);
  assert.match(source, /sessionStorage\.setItem\("vc-basic"/);
  assert.match(source, /router\.replace\("\/dashboard"\)/);
  assert.doesNotMatch(source, /does not yet create accounts/);
});

test("signup surfaces duplicate-account and API failures instead of redirecting", async () => {
  const source = await readFile(new URL("../app/signup/page.tsx", import.meta.url), "utf8");
  assert.match(source, /An account with this email already exists/);
  assert.match(source, /role=\{isError \? "alert" : "status"\}/);
  assert.match(source, /response\.ok/);
});
