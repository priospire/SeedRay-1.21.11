package com.seedxray.client;

public final class XrayConstants {
    public static final String MOD_ID = "seed-xray";
    public static final String MOD_NAME = "Seed X-Ray";

    public static final int DEFAULT_PREDICTION_RADIUS_CHUNKS = 8;
    public static final int DEFAULT_DIAGNOSTIC_RADIUS_CHUNKS = 4;
    public static final int DEFAULT_MAX_PREDICTED_CHUNKS_PER_TICK = 1;
    public static final int DEFAULT_MAX_DIAGNOSTIC_CHUNKS_PER_TICK = 1;
    public static final int DEFAULT_MAX_RENDERED_HIGHLIGHTS = 1000;
    public static final int DEFAULT_DISTANCE_LIMIT_BLOCKS = 192;
    public static final int PREDICTION_ORIGIN_MARGIN_CHUNKS = 2;

    private XrayConstants() {
    }
}
