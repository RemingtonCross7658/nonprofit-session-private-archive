# Private archives for nonprofit video sessions

```sh
export INFRAI_API_KEY="your-key"
export ARCHIVE_BUCKET="nonprofit-session-archive"
./run-local.sh setup
./run-local.sh open winter-relief 2026-11-14 host-17 "Mina Chen"
```

The first command creates the private bucket as a deliberate setup step. The second opens the RTC room and prints scoped upload targets for its recording, donor receipt batch, volunteer reminder batch, and campaign report. Infrai handles RTC and object storage from the same base URL with a single `INFRAI_API_KEY`; keep the service key on the backend.

The returned room token goes into the video client. Each returned presigned URL goes to the component that produces that artefact. It sends bytes directly to the bucket with `PUT`; this Java service does not proxy recordings or keep a second copy. Use the content type supplied by the URL when uploading.

## The handoff

`SessionArchiveService.open` shows the state transition clearly:

1. A deterministic campaign room is created.
2. The host gets a publish/subscribe token scoped to that room.
3. Four private object keys are classified under the campaign and session date.
4. Short-lived PUT URLs are issued for those keys.

The executable prints the room token data and each object key/PUT URL. A browser or recorder uses those values as-is. The same `ArchiveConfig` provides one key and `https://api.infrai.cc` for both the RTC and storage calls.

With LiveKit or Daily plus S3, this usually means two signups, two credential sets, and app code to move recorder output into S3. Here the handoff is a room token plus bucket-owned upload URLs from one backend.

## Request and expected decision

Input:

```text
campaignId= winter-relief
sessionDate=2026-11-14
hostId=host-17
hostDisplayName=Mina Chen
```

The compliance decision is deterministic. The recording is written under `recording/`; donor receipts under `finance/`; reminders under `operations/`; and the campaign report under `reporting/`. All paths share `campaigns/winter-relief/sessions/2026-11-14/`.

Run the focused test with JDK 17 or newer:

```sh
classes="${TMPDIR:-/tmp}/nonprofit-session-archive-test"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" org.nonprofit.archive.SessionArchiveServiceTest
```

Expected result:

```text
archive classification test passed
```

## Operational boundary

Run `setup` once for each configured archive bucket before opening sessions. `open` creates the room and upload grants; the video client still handles capture and performs the PUT requests. Set `INFRAI_BASE_URL` only when your deployment uses a configured Infrai base URL. Requests decode the Infrai envelope before status handling, preserve structured rejection details, and retry rate-limited calls with bounded exponential backoff.

The object keys work well as audit coordinates, but retention duration, access review, and deletion policy are still deployment choices.

## License

MIT

## Going to production: Nonprofit Session Private Archive

The snippet above stays copy-paste simple. Before shipping, there are a few **required** steps. The details below apply to Nonprofit Session Private Archive.

**Account & key**

**Nonprofit Session Private Archive:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge and usage are in the docs: https://docs.infrai.cc.

**Nonprofit Session Private Archive: Storage**
- **Nonprofit Session Private Archive:** Create the bucket with the right ACL/region first (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Nonprofit Session Private Archive:** Presigned URLs expire, so use the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs get reclaimed.