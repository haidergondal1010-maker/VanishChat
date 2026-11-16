# VanishChat API Specification

## 1. Architecture

VanishChat uses a centralized relay-server architecture.

- Android clients communicate with a central FastAPI server.
- The server uses SQLite for message and participant storage.
- Communication uses REST API endpoints with JSON request and response bodies.
- Clients retrieve messages through periodic polling.
- All devices in the same room share messages through the relay.

## 2. Device Registration

### POST /join

Registers a device in a room.

Request:
- device_id
- display_name
- room_id

Response:
- joined
- device_id
- display_name
- room_id
- server_time

## 3. Sending Messages

### POST /messages

Request:
- id: Unique client-generated message ID.
- room_id: Destination room.
- sender_id: Registered device ID.
- text: Message content.

The server:
1. Verifies that the sender is registered in the specified room.
2. Rejects empty or invalid messages.
3. Assigns the authoritative acceptance timestamp.
4. Sets expires_at to accepted_at + 30,000 milliseconds.
5. Assigns a server sequence number.
6. Stores the message.

A repeated message ID must not create duplicate messages.

Response:
- id
- room_id
- sender_id
- sender_name
- text
- accepted_at
- expires_at
- seq

## 4. Receiving Messages

### GET /messages?room_id={room_id}&since={cursor}

Returns messages with sequence numbers greater than the client's cursor.

The response contains:
- messages: Active messages.
- cursor: Latest sequence number processed.
- has_more: Indicates whether another page is available.

Clients poll approximately once per second while the chat screen is active.

## 5. Message Expiration

- The server is authoritative for message expiration.
- A message expires 30 seconds after server acceptance.
- Expired message content is cleared from the server database.
- Sequence metadata is retained so clients can advance their polling cursors.
- Expired messages are never returned as active messages.
- Clients remove locally cached messages after their expiration time.

## 6. Idempotency

Each message has a unique client-generated ID.

If a client retries a message after a timeout, the server must not create a duplicate message.

## 7. Existing Field Sync API

The existing endpoints remain available:

- GET /health
- POST /sync/push
- GET /sync/pull
- GET /records/{id}

These endpoints are not replaced by the messaging API.

## 8. Current Limitations

- The initial implementation does not provide end-to-end encryption.
- Room registration does not yet provide strong authentication.
- Polling is intended for active foreground chat sessions.
- Public deployment and production security hardening are separate tasks.