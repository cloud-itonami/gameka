# gameka.gftd.ai — Clojure edition

CLJ graph server replacing the old Python `lg_gameka` LangGraph scaffold.

Surface:

- `GET /health`, `/ok`
- `POST /runs`
- `POST /xrpc/ai.gftd.apps.gameka.generate`
- `POST /xrpc/ai.gftd.apps.gameka.proposeSpec`
- `POST /xrpc/ai.gftd.gameka.{proposeGame,generateGame,playtestGame,publishGame}`

`generate` remains an honest scaffold: it validates `prompt` and returns
`status=not-implemented` with an empty `blobCid` until a real game asset
generator is wired.

```bash
kbb -M:test
PORT=8000 kbb -M:run
```
