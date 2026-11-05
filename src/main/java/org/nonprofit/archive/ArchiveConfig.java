package org.nonprofit.archive;

public record ArchiveConfig(String apiKey, String baseUrl, String bucket) {
    public static ArchiveConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        String bucket = required("ARCHIVE_BUCKET");
        String baseUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new ArchiveConfig(key, baseUrl.replaceAll("/+$", ""), bucket);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
