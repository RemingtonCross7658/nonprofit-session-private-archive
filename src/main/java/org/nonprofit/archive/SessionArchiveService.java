package org.nonprofit.archive;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SessionArchiveService {
    private final InfraiClient infrai;
    private final ArchiveConfig config;

    public SessionArchiveService(InfraiClient infrai, ArchiveConfig config) {
        this.infrai = infrai;
        this.config = config;
    }

    public void setUpPrivateArchive() throws IOException, InterruptedException {
        infrai.createBucket(config.bucket());
    }

    public SessionHandoff open(SessionRequest request) throws IOException, InterruptedException {
        String room = "campaign-" + safe(request.campaignId()) + "-" + request.sessionDate();
        infrai.createRoom(room);
        Map<String, Object> token = infrai.issueRoomToken(room, request.hostId(), request.hostDisplayName());
        Map<ArtifactKind, UploadTarget> uploads = new LinkedHashMap<>();
        for (ArtifactKind kind : ArtifactKind.values()) {
            String key = objectKey(request, kind);
            String requestId = room + ":" + kind.name().toLowerCase();
            String url = infrai.presignPut(config.bucket(), key, kind.contentType, kind.maxBytes, requestId);
            uploads.put(kind, new UploadTarget(key, url, kind.contentType, kind.maxBytes));
        }
        return new SessionHandoff(room, token, Map.copyOf(uploads));
    }

    static String objectKey(SessionRequest request, ArtifactKind kind) {
        String root = "campaigns/" + safe(request.campaignId()) + "/sessions/" + request.sessionDate();
        return switch (kind) {
            case RECORDING -> root + "/recording/session.webm";
            case DONOR_RECEIPTS -> root + "/finance/donor-receipts.json";
            case VOLUNTEER_REMINDERS -> root + "/operations/volunteer-reminders.json";
            case CAMPAIGN_REPORT -> root + "/reporting/campaign-report.json";
        };
    }

    private static String safe(String value) {
        if (!value.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Identifier must contain only letters, digits, underscore, or hyphen");
        }
        return value;
    }

    public enum ArtifactKind {
        RECORDING("video/webm", 1_500_000_000L),
        DONOR_RECEIPTS("application/json", 5_000_000L),
        VOLUNTEER_REMINDERS("application/json", 5_000_000L),
        CAMPAIGN_REPORT("application/json", 10_000_000L);

        final String contentType;
        final long maxBytes;
        ArtifactKind(String contentType, long maxBytes) {
            this.contentType = contentType;
            this.maxBytes = maxBytes;
        }
    }

    public record SessionRequest(String campaignId, LocalDate sessionDate, String hostId, String hostDisplayName) {}
    public record UploadTarget(String objectKey, String uploadUrl, String contentType, long maxBytes) {}
    public record SessionHandoff(String room, Map<String, Object> roomToken, Map<ArtifactKind, UploadTarget> uploads) {}
}
