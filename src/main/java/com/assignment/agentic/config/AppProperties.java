package com.assignment.agentic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Shortener shortener = new Shortener();
    private final Orchestrator orchestrator = new Orchestrator();

    public Shortener getShortener() {
        return shortener;
    }

    public Orchestrator getOrchestrator() {
        return orchestrator;
    }

    public static class Shortener {
        private int codeLength = 7;
        private int maxUrlLength = 2048;
        private int rateLimitPerMinute = 30;
        private String hmacSecret = "local-dev-hmac-change-me";

        public int getCodeLength() {
            return codeLength;
        }

        public void setCodeLength(int codeLength) {
            this.codeLength = codeLength;
        }

        public int getMaxUrlLength() {
            return maxUrlLength;
        }

        public void setMaxUrlLength(int maxUrlLength) {
            this.maxUrlLength = maxUrlLength;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(int rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }
    }

    public static class Orchestrator {
        private int maxRetries = 3;
        private long retryBackoffMs = 80;
        private String artifactDir = "./data/artifacts";

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public String getArtifactDir() {
            return artifactDir;
        }

        public void setArtifactDir(String artifactDir) {
            this.artifactDir = artifactDir;
        }
    }
}
