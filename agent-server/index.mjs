import { timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";
import OpenAI from "openai";

const port = Number(process.env.PORT ?? 3000);
const accessToken = process.env.AGENT_ACCESS_TOKEN;
const apiKey = process.env.OPENAI_API_KEY;
const model = process.env.OPENAI_MODEL ?? "gpt-4.1-mini";
const maxBodyBytes = 16 * 1024;
const maxMessageChars = 4_000;

if (!apiKey) throw new Error("OPENAI_API_KEY is required.");
if (!accessToken || accessToken.length < 32) {
  throw new Error("AGENT_ACCESS_TOKEN is required and must be at least 32 characters.");
}

const openai = new OpenAI({ apiKey });
const instructions = `You are MicArbiter Assistant, a concise support agent for the MicArbiter Android app.
Help users understand the app's microphone arbitration controls, demo mode, permissions, and troubleshooting.
Be clear that Android limits one app's ability to mute or intercept another app's microphone; do not promise control the operating system does not provide.
You cannot see or control the user's device, microphone, or app state. Never claim that you changed a setting or accessed microphone audio.
For microphone privacy or safety questions, recommend using MicArbiter's in-app controls and Android system settings.`;

function sendJson(response, status, value) {
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  response.end(JSON.stringify(value));
}

function isAuthorized(request) {
  const header = request.headers.authorization ?? "";
  const supplied = header.startsWith("Bearer ") ? header.slice(7) : "";
  const suppliedBuffer = Buffer.from(supplied);
  const expectedBuffer = Buffer.from(accessToken);
  return suppliedBuffer.length === expectedBuffer.length && timingSafeEqual(suppliedBuffer, expectedBuffer);
}

async function readJson(request) {
  const chunks = [];
  let bytes = 0;
  for await (const chunk of request) {
    bytes += chunk.length;
    if (bytes > maxBodyBytes) throw Object.assign(new Error("Request body too large."), { status: 413 });
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw Object.assign(new Error("Request body must be valid JSON."), { status: 400 });
  }
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);

  if (request.method === "GET" && url.pathname === "/health") {
    return sendJson(response, 200, { ok: true });
  }

  if (request.method !== "POST" || url.pathname !== "/api/chat") {
    return sendJson(response, 404, { error: "Not found." });
  }
  if (!isAuthorized(request)) {
    return sendJson(response, 401, { error: "Unauthorized." });
  }
  if (!request.headers["content-type"]?.includes("application/json")) {
    return sendJson(response, 415, { error: "Content-Type must be application/json." });
  }

  try {
    const body = await readJson(request);
    const message = typeof body.message === "string" ? body.message.trim() : "";
    if (!message || message.length > maxMessageChars) {
      return sendJson(response, 400, { error: `message must be 1-${maxMessageChars} characters.` });
    }

    const result = await openai.responses.create({
      model,
      instructions,
      input: message,
      store: false,
    });
    return sendJson(response, 200, { reply: result.output_text });
  } catch (error) {
    if (error.status) return sendJson(response, error.status, { error: error.message });
    console.error("Agent request failed:", error.message);
    return sendJson(response, 502, { error: "The assistant could not complete the request. Please try again." });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`MicArbiter agent server listening on port ${port}`);
});
