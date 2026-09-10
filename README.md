# FriendsSoft SMS Server

Android local SMS HTTP server for FriendsSoft.

## API
- `GET /` - status page
- `GET /v1/device/status` - device/server status JSON
- `GET /v1/thread` - compatibility endpoint
- `POST /send-message` - send SMS
- `POST /v1/sms` - send SMS compatibility endpoint
- `POST /sms` - send SMS compatibility endpoint

POST JSON:
`{"number":"03001234567","message":"Hello from FriendsSoft"}`

## SIM handling
The server requests both `SEND_SMS` and `READ_PHONE_STATE`, uses Android's default SMS subscription when available, and falls back to the first active SIM subscription. The SMS request is queued through the selected subscription rather than relying only on `SmsManager.getDefault()`.
