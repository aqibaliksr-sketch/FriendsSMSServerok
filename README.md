# FriendsSoft SMS Server

Android project inspired by the uploaded Innovators Soft SMS Server APK. It provides a small local HTTP server and sends SMS through the Android phone's SIM.

## API
- `GET /` - status page
- `GET /v1/device/status` - device/server status JSON
- `GET /v1/thread` - compatibility endpoint
- `POST /send-message` - send SMS
- `POST /v1/sms` - send SMS compatibility endpoint

POST JSON example:
`{"number":"03001234567","message":"Hello from FriendsSoft"}`

Open in Android Studio and build the APK. SEND_SMS permission is requested on first launch.
