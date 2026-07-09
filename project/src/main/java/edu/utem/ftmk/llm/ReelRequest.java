package edu.utem.ftmk.llm;

import java.io.Serializable;

public class ReelRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public int reelId;
    public String modelName;
    public String promptTechnique;
    
    public String action = "ANALYZE"; 
    public String exportLayer = "";

    public ReelRequest() {}

    public ReelRequest(int reelId, String modelName, String promptTechnique) {
        this.reelId = reelId;
        this.modelName = modelName;
        this.promptTechnique = promptTechnique;
    }
}