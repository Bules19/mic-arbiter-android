
## OpenAI-powered support agent

An optional Node.js backend is included in [`agent-server/`](agent-server/README.md). It uses the OpenAI Responses API to answer MicArbiter support questions. Run/deploy that backend separately; keep the OpenAI API key on the server, not in the Android app. The backend's `/api/chat` endpoint requires a bearer token and has no access to device controls or microphone audio.
