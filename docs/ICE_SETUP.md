# STUN/TURN – Qué son y cómo configurarlos (coturn)

## ¿Qué hacen?
- **STUN**: descubre tu **IP:puerto públicos** detrás del NAT. No retransmite audio/video. Ayuda cuando hay conectividad P2P directa.
- **TURN**: actúa como **relay** cuando P2P falla (NAT simétricos, firewalls corporativos, Wi‑Fi estrictos). Incrementa confiabilidad (a costa de ancho de banda del servidor).

Para llamadas estables (SLO 1 hora, ≤1% error), **TURN es recomendado**.

---

## Pasos para montar TURN con *coturn*

1) **Servidor público** (Ubuntu/Debian) con IP pública fija y DNS `turn.uplearn.edu` apuntando a esa IP.
2) **Puertos (firewall)** abrir:
   - `3478/udp` y `3478/tcp` (TURN sin TLS)
   - `5349/tcp` (TURN con TLS)
   - Rango UDP para medios, por ejemplo: `49160-49200/udp` (ajustable con `min-port`/`max-port`)
3) **Instalar coturn**  
   ```bash
   sudo apt update && sudo apt install -y coturn
   ```
4) **Certificados (opcional pero recomendado para TLS)**  
   Usar Let’s Encrypt para `turn.uplearn.edu` y ubicar en:
   - `/etc/letsencrypt/live/turn.uplearn.edu/fullchain.pem`
   - `/etc/letsencrypt/live/turn.uplearn.edu/privkey.pem`
5) **Configurar `/etc/turnserver.conf`** (o usar el ejemplo en `deploy/coturn/turnserver.conf`):
   - Con **credenciales estáticas** (rápido para pruebas):
     ```
     listening-port=3478
     tls-listening-port=5349
     listening-ip=0.0.0.0
     relay-ip=<PUBLIC_IP>
     min-port=49160
     max-port=49200

     realm=uplearn.edu
     server-name=turn.uplearn.edu
     fingerprint
     lt-cred-mech
     user=upl-rtc:ChangeMe!Strong

     cert=/etc/letsencrypt/live/turn.uplearn.edu/fullchain.pem
     pkey=/etc/letsencrypt/live/turn.uplearn.edu/privkey.pem
     no-tlsv1
     no-tlsv1_1
     ```
   - Con **REST auth secreta** (producción; credenciales efímeras):
     ```
     listening-port=3478
     tls-listening-port=5349
     listening-ip=0.0.0.0
     relay-ip=<PUBLIC_IP>
     min-port=49160
     max-port=49200

     realm=uplearn.edu
     server-name=turn.uplearn.edu
     fingerprint
     use-auth-secret
     static-auth-secret=<HEX_SECRET>   # compártelo solo con tu backend
     cert=/etc/letsencrypt/live/turn.uplearn.edu/fullchain.pem
     pkey=/etc/letsencrypt/live/turn.uplearn.edu/privkey.pem
     no-tlsv1
     no-tlsv1_1
     ```
6) **Levantar el servicio**  
   ```bash
   sudo systemctl enable coturn
   sudo systemctl start coturn
   sudo systemctl status coturn
   ```

---

## Backend – configurar `ICE_SERVERS_JSON`
En `application.yml` o variable de entorno:
```json
[
  {"urls":["stun:stun1.l.google.com:19302"]},
  {"urls":[
    "turn:turn.uplearn.edu:3478?transport=udp",
    "turn:turn.uplearn.edu:3478?transport=tcp",
    "turns:turn.uplearn.edu:5349?transport=tcp"
  ],
   "username":"upl-rtc",
   "credential":"ChangeMe!Strong",
   "ttl":3600}
]
```
> Si usas **REST auth secreta**, tu backend debe generar `username` y `credential` con HMAC y vencimiento corto.

## Client (ya listo)
El front obtiene ICE con `GET /api/calls/ice-servers` y negocia WebRTC. No requiere cambios adicionales.
