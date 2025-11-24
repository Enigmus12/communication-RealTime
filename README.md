# call-service (sin OIDC; patrón igual a mensajería)
- Autenticación y roles: se extraen del **JWT** recibido (header Authorization, `?token` o cookie `access_token`), tal como mensajería (decode base64 + check exp). 
- Señalización WebRTC por **WebSocket**; **Redis pub/sub** (`call:{sessionId}`) para escalar sin sticky-session.
- Persistencia Mongo con TTL (~70 min) + **auto-END** a 60 min.

## Levantar
1. Abrir docker desktop en el computador y esperar que inicie
2. Dentro de la carpeta raiz del proyecto poner:
```
docker compose up -d coturn call-service --no-deps
mvn spring-boot:run
```

---

## Config (.env)
DB_URI, DB_NAME=calls, SPRING_DATA_MONGODB_AUTO_INDEX_CREATION
REDIS_HOST=localhost, REDIS_PORT=6379
USERS_BASE, USERS_PUBLIC_PATH, RESERVATIONS_BASE
ICE_SERVERS_JSON (solo STUN por defecto)

## WS
`wss://<gw>/ws/call?token=<ACCESS_TOKEN>`

## REST
POST `/api/calls/session` { reservationId } → { sessionId, reservationId, ttlSeconds }
POST `/api/calls/{sessionId}/end`
GET `/api/calls/ice-servers`

## Notas
- Rate-limit 20 msg/s por conexión; heartbeat 10 s; idle 30 s.
- No se loguean payloads SDP/ICE.
