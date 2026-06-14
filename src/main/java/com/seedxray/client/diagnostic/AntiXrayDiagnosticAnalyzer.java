package com.seedxray.client.diagnostic;

import com.seedxray.client.prediction.OrePredictionRecord;
import com.seedxray.client.prediction.PredictionVisibilityStatus;
import com.seedxray.client.util.BlockUtil;
import net.minecraft.block.BlockState;

public final class AntiXrayDiagnosticAnalyzer {
    public void applyClientSeenState(OrePredictionRecord record, BlockState clientState, long tick) {
        record.setClientSeenBlockState(clientState);
        record.setLastDiagnosticScanTick(tick);
        if (BlockUtil.matchesOreFamily(clientState, record.oreTarget())) {
            record.setVisibilityStatus(PredictionVisibilityStatus.PREDICTED_AND_CLIENT_MATCHES);
        } else if (BlockUtil.isTrustedClientObstruction(clientState)) {
            record.setVisibilityStatus(PredictionVisibilityStatus.PREDICTED_CLIENT_OBSTRUCTED);
        } else {
            record.setVisibilityStatus(PredictionVisibilityStatus.PREDICTED_BUT_CLIENT_MASKED);
        }
    }

    public void markUnloaded(OrePredictionRecord record, long tick) {
        record.setClientSeenBlockState(null);
        record.setLastDiagnosticScanTick(tick);
        record.setVisibilityStatus(PredictionVisibilityStatus.UNKNOWN_UNLOADED);
    }
}
