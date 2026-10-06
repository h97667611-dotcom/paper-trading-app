# Demo Slot API (nur Demo, kein Echtgeld)

20 erfundene Demo-Slots mit simulierten Spins als kleine REST-API (Node.js >= 18, ohne Abhaengigkeiten).
Keine Registrierung, kein API-Key, keine Zahlungen. Jede Antwort enthaelt `"demo": true`.

Start: `cd demo-slot-api && node server.js` (http://127.0.0.1:3000, Beispiel-Frontend unter /demo).

Endpunkte (nur GET, CORS offen): `/api/slots` (Filter: category, provider, mechanic, q; Paging: page, limit),
`/api/slots/:id`, `/api/slots/:id/spin?seed=`, `/api/categories`, `/api/providers`, `/health`.

Die gleichen Slots und die gleiche Spin-Logik sind in der Android-App nativ eingebaut (Casino-Tab):
`app/src/main/java/com/papertrader/app/data/casino/DemoSlots.kt` und `SlotEngine.kt`.
