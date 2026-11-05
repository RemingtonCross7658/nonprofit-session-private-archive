package org.nonprofit.archive;

import java.time.LocalDate;

public final class SessionArchiveServiceTest {
    public static void main(String[] args) {
        var request = new SessionArchiveService.SessionRequest(
                "winter-relief", LocalDate.parse("2026-11-14"), "host-17", "Mina Chen");
        assertKey(request, SessionArchiveService.ArtifactKind.RECORDING,
                "campaigns/winter-relief/sessions/2026-11-14/recording/session.webm");
        assertKey(request, SessionArchiveService.ArtifactKind.DONOR_RECEIPTS,
                "campaigns/winter-relief/sessions/2026-11-14/finance/donor-receipts.json");
        assertKey(request, SessionArchiveService.ArtifactKind.VOLUNTEER_REMINDERS,
                "campaigns/winter-relief/sessions/2026-11-14/operations/volunteer-reminders.json");
        assertKey(request, SessionArchiveService.ArtifactKind.CAMPAIGN_REPORT,
                "campaigns/winter-relief/sessions/2026-11-14/reporting/campaign-report.json");
        System.out.println("archive classification test passed");
    }

    private static void assertKey(SessionArchiveService.SessionRequest request,
                                  SessionArchiveService.ArtifactKind kind,
                                  String expected) {
        String actual = SessionArchiveService.objectKey(request, kind);
        if (!expected.equals(actual)) throw new AssertionError(kind + ": " + actual);
    }
}
