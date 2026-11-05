package org.nonprofit.archive;

import java.time.LocalDate;

public final class NonprofitSessionRunner {
    private NonprofitSessionRunner() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !(args[0].equals("setup") || args[0].equals("open"))) {
            System.err.println("Usage: NonprofitSessionRunner setup | open <campaign-id> <yyyy-mm-dd> <host-id> <host-name>");
            System.exit(2);
        }
        ArchiveConfig config = ArchiveConfig.fromEnvironment();
        SessionArchiveService service = new SessionArchiveService(new InfraiClient(config), config);
        if (args[0].equals("setup")) {
            service.setUpPrivateArchive();
            System.out.println("Private archive bucket is ready: " + config.bucket());
            return;
        }
        if (args.length != 5) throw new IllegalArgumentException("open requires four arguments");
        var handoff = service.open(new SessionArchiveService.SessionRequest(
                args[1], LocalDate.parse(args[2]), args[3], args[4]));
        System.out.println("Room: " + handoff.room());
        System.out.println("Room token data: " + handoff.roomToken());
        handoff.uploads().forEach((kind, target) -> {
            System.out.println(kind + " object: " + target.objectKey());
            System.out.println(kind + " PUT URL: " + target.uploadUrl());
        });
    }
}
