# MicArbiter OpenAI agent server

This small Node.js service runs a MicArbiter support agent through the OpenAI Responses API. It is intended to be deployed separately from the Android app so the OpenAI API key stays on the server. The agent answers app questions only; it has no tools to access the device or control the microphone.

## Run locally

Requires Node.js 20 or newer. From this directory:

1. Copy `.env.example` to `.env` and set `OPENAI_API_KEY` and a randomly generated `AGENT_ACCESS_TOKEN` of at least 32 characters.
2. Install dependencies with `npm install`.
3. Export the two values from `.env` into your shell (or use a local environment-file loader), then run `npm start`.
4. Check `GET http://localhost:3000/health`.

The server exposes `POST /api/chat`, expects JSON `{ "message": "..." }`, and requires `Authorization: Bearer <AGENT_ACCESS_TOKEN>`. Example:

```sh
curl -X POST http://localhost:3000/api/chat \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer YOUR_AGENT_ACCESS_TOKEN' \
  -d '{"message":"What does Demo mode do?"}'
```

Set `PORT` or `OPENAI_MODEL` to override the defaults. The default model is `gpt-4.1-mini`. The request uses `store: false`; it does not persist conversation state between requests.

## Production notes

Deploy behind HTTPS. Do not commit `.env`, ship the OpenAI key in the Android APK, or expose an unauthenticated endpoint. The bearer token is a basic shared-secret gate, not user identity: a token embedded in a distributed app can be extracted. For a public app, replace it with per-user authentication and add rate limiting/usage controls at the gateway before deployment. `AGENT_ACCESS_TOKEN` should be provided through the hosting platform's secret manager.
