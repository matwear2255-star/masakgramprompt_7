package edu.utem.ftmk.llm;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

public class ReelResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    public int reelId;
    public String influencerHandle;
    public int videoDuration;
    public String languageTag;
    public String transcriptStatus;
    public boolean groundTruthAvailable;

    public String transcriptPreview;
    public String jsonOutput;
    public String status;
    public int totalTranscripts = 0;
    public int batchTotal = 0;
    public int batchSuccess = 0;
    public int batchFailed = 0;
    public String logMessage = "";

    public HashMap<String, String> conditionStatuses = new HashMap<>();
    public String factSheetView = "";
    public String csvPayload    = "";

    // STATUS_MATRIX
    public List<Object[]> matrixRows = null;

    // REEL_DETAIL mini table
    public List<Object[]> reelStatusRows = null;

    // FACT_SHEET_DATA
    public List<Object[]> gtRows  = null;
    public List<Object[]> llmRows = null;
    public double gtTotalCalories    = 0;
    public double llmTotalCalories   = 0;
    public double llmServingCalories = 0;
    public int    llmServings        = 0;

    public transient String audioFilePath;
    public transient String transcriptFilePath;

    public ReelResponse() {}
}