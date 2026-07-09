package edu.utem.ftmk.llm;

import java.io.File;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class MasakGramClient {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==================================================");
        System.out.println("🍳 [CLIENT] BATCH MASAKGRAM EXPERIMENT TERMINAL");
        System.out.println("==================================================");

        try {
            System.out.println("\n[Step 1] Enter Transcript IDs (comma separated, e.g. 1,2,8,47,50): ");
            String[] idInput = scanner.nextLine().trim().split(",");
            List<Integer> reelIds = new ArrayList<>();
            for (String id : idInput) reelIds.add(Integer.parseInt(id.trim()));

            System.out.println("\n[Step 2] Select LLM Models (comma separated, e.g. 1,3,4,5):");
            System.out.println("1. Llama 3.2 3B"); 
            System.out.println("2. Phi-4-mini"); 
            System.out.println("3. Qwen 2.5 3B"); 
            System.out.println("4. Gemma-SEA-LION v4 4B"); 
            System.out.println("5. MedGemma 4B");
            String[] modelInput = scanner.nextLine().trim().split(",");
            List<String> models = new ArrayList<>();
            for (String m : modelInput) {
                switch (Integer.parseInt(m.trim())) { 
                    case 1: models.add("llama3.2:3b"); break;
                    case 2: models.add("phi4-mini"); break;
                    case 3: models.add("qwen2.5:3b"); break;
                    case 4: models.add("aisingapore/Gemma-SEA-LION-v4-4B-VL"); break;
                    case 5: models.add("medgemma:4b"); break;
                }
            }

            System.out.println("\n[Step 3] Select Prompt Techniques (comma separated, e.g. 1,4):");
            System.out.println("1. zero_shot"); 
            System.out.println("2. few_shot"); 
            System.out.println("3. chain_of_thought"); 
            System.out.println("4. structured_output");
            String[] techInput = scanner.nextLine().trim().split(",");
            List<String> techniques = new ArrayList<>();
            for (String t : techInput) {
                switch (Integer.parseInt(t.trim())) { 
                    case 1: techniques.add("zero_shot"); break;
                    case 2: techniques.add("few_shot"); break;
                    case 3: techniques.add("chain_of_thought"); break;
                    case 4: techniques.add("structured_output"); break;
                }
            }

            System.out.println("\n🚀 Commencing Sequential Batch Processing...");
            int totalRuns = reelIds.size() * models.size() * techniques.size();
            int currentRun = 1;

            for (int reelId : reelIds) {
                for (String modelName : models) {
                    for (String technique : techniques) {
                        System.out.println("\n--------------------------------------------------");
                        System.out.printf("🔄 RUN [%d/%d] -> ID: %d | Model: %s | Tech: %s\n", currentRun++, totalRuns, reelId, modelName, technique);
                        System.out.println("--------------------------------------------------");

                        ReelResponse response = sendRequest(new ReelRequest(reelId, modelName, technique));

                        if ("Failed".equals(response.status)) { 
                            System.out.println(response.jsonOutput); 
                            continue; 
                        }

                        // REEL DETAILS DASHBOARD [REQ 4.3a]
                        System.out.println("\n📊 REEL METADATA PROPERTIES:");
                        System.out.printf("🔹 Reel ID             : %d\n", response.reelId);
                        System.out.printf("🔹 Influencer Handle   : @%s\n", response.influencerHandle);
                        System.out.printf("🔹 Video Duration      : %d seconds\n", response.videoDuration);
                        System.out.printf("🔹 Language Profiling  : %s\n", response.languageTag);
                        System.out.printf("🔹 Transcript Status   : %s\n", response.transcriptStatus);
                        System.out.printf("🔹 Ground Truth Ready  : %b\n", response.groundTruthAvailable);
                        System.out.println("--------------------------------------------------");
                        
                        // NO COLORS [REQ 4.3b]
                        System.out.println("📝 COMPLETE TRANSCRIPT PREVIEW:\n");
                        System.out.println(response.transcriptPreview);
                        System.out.println("\n--------------------------------------------------");
                        System.out.println("✅ AI Extracted Nutritional Data parsed and saved to Database.");
                    }
                }
            }

            System.out.println("\n🎉 BATCH PROCESSING COMPLETE.");

            // POST-PROCESSING INTERACTIVE MENU [REQ 4.4 & 4.5]
            while (true) {
                System.out.println("\n🔗 POST-RUN ACTIONS:");
                System.out.println("👉 [N] View Nutritional Fact Sheet (Side-by-side comparison for a specific run)");
                System.out.println("👉 [E] Export Academic CSV Metrics (Export 1 of 10 specific layers)");
                System.out.println("👉 [Q] Quit Session");
                System.out.print("Action: ");
                String action = scanner.nextLine().trim().toUpperCase();

                if ("Q".equals(action)) {
                    System.out.println("Session terminated safely."); break;
                } else if ("N".equals(action)) {

                    System.out.print("Enter Transcript ID to view: ");
                    int tId = Integer.parseInt(scanner.nextLine().trim());

                    // MODEL SELECTION
                    System.out.println("\n========================================");
                    System.out.println("SELECT LLM MODEL");
                    System.out.println("========================================");
                    System.out.println("1. Llama 3.2 3B");
                    System.out.println("2. Phi-4-mini");
                    System.out.println("3. Qwen 2.5 3B");
                    System.out.println("4. Gemma-SEA-LION v4 4B");
                    System.out.println("5. MedGemma 4B");
                    System.out.print("Choice (1-5): ");

                    int modelChoice = Integer.parseInt(scanner.nextLine().trim());
                    String mName;
                    switch (modelChoice) {
                        case 1: mName = "llama3.2:3b"; break;
                        case 2: mName = "phi4-mini"; break;
                        case 3: mName = "qwen2.5:3b"; break;
                        case 4: mName = "aisingapore/Gemma-SEA-LION-v4-4B-VL"; break;
                        case 5: mName = "medgemma:4b"; break;
                        default:
                            System.out.println("❌ Invalid model selection.");
                            continue;
                    }

                    // PROMPT TECHNIQUE SELECTION
                    System.out.println("\n========================================");
                    System.out.println("SELECT PROMPT TECHNIQUE");
                    System.out.println("========================================");
                    System.out.println("1. Zero Shot");
                    System.out.println("2. Few Shot");
                    System.out.println("3. Chain of Thought");
                    System.out.println("4. Structured Output");
                    System.out.print("Choice (1-4): ");

                    int techniqueChoice = Integer.parseInt(scanner.nextLine().trim());
                    String pTech;
                    switch (techniqueChoice) {
                        case 1: pTech = "zero-shot"; break;
                        case 2: pTech = "few-shot"; break;
                        case 3: pTech = "chain-f-thought"; break;
                        case 4: pTech = "structured-output"; break;
                        default:
                            System.out.println("❌ Invalid prompt technique selection.");
                            continue;
                    }

                    System.out.println("\n========================================");
                    System.out.println("GENERATING FACT SHEET");
                    System.out.println("========================================");
                    System.out.println("Transcript ID : " + tId);
                    System.out.println("Model         : " + mName);
                    System.out.println("Technique     : " + pTech);

                    ReelRequest req = new ReelRequest(tId, mName, pTech);
                    req.action = "FACT_SHEET";
                    ReelResponse sheetResp = sendRequest(req);
                    System.out.println("\n========================================");
                    System.out.println("NUTRITIONAL FACT SHEET");
                    System.out.println("========================================");
                    System.out.println(sheetResp.factSheetView);
                    System.out.println("========================================");
                    
                } else if ("E".equals(action)) { // <-- THE FIX IS HERE. Only ONE closing bracket `}` precedes this.
                    
                    System.out.println("\n📦 Select Data Export Layer (1-10):");
                    System.out.println("1. LAYER_1A (Exact match) \n2. LAYER_1B (Text similarity) \n3. LAYER_2A (Numeric quantity)");
                    System.out.println("4. LAYER_2B (Numeric nutrition)\n5. LAYER_2C (Nutrition totals)\n6. LAYER_3A (JSON validity)");
                    System.out.println("7. LAYER_3B (Hallucination)\n8. LAYER_3C (Ingredient detection)\n9. LAYER_4 (Human evaluation)\n10. LAYER_5 (Condition scores)");
                    System.out.print("Select (1-10): ");
                    int layerChoice = Integer.parseInt(scanner.nextLine().trim());
                    
                    String exportLayer = "";
                    String fileName = "";
                    switch (layerChoice) { 
                        case 1: exportLayer = "LAYER_1A"; fileName = "layer1a_exact_match.csv"; break;
                        case 2: exportLayer = "LAYER_1B"; fileName = "layer1b_text_similarity.csv"; break;
                        case 3: exportLayer = "LAYER_2A"; fileName = "layer2a_numeric_quantity.csv"; break;
                        case 4: exportLayer = "LAYER_2B"; fileName = "layer2b_numeric_nutrition.csv"; break;
                        case 5: exportLayer = "LAYER_2C"; fileName = "layer2c_nutrition_totals.csv"; break;
                        case 6: exportLayer = "LAYER_3A"; fileName = "layer3a_json_validity.csv"; break;
                        case 7: exportLayer = "LAYER_3B"; fileName = "layer3b_hallucination.csv"; break;
                        case 8: exportLayer = "LAYER_3C"; fileName = "layer3c_ingredient_detection.csv"; break;
                        case 9: exportLayer = "LAYER_4";  fileName = "layer4_human_evaluation.csv"; break;
                        case 10: exportLayer = "LAYER_5"; fileName = "layer5_condition_scores.csv"; break;
                        default: exportLayer = "LAYER_1A"; fileName = "layer1a_exact_match.csv"; break; 
                    }
                    
                    ReelRequest expReq = new ReelRequest(0, "", "");
                    expReq.action = "EXPORT"; expReq.exportLayer = exportLayer;
                    ReelResponse expResp = sendRequest(expReq);
                    
                    if (expResp.csvPayload.startsWith("❌")) { 
                        System.out.println(expResp.csvPayload); 
                    } else {
                        File file = new File(fileName);
                        try (FileWriter writer = new FileWriter(file)) { 
                            writer.write(expResp.csvPayload); 
                            System.out.println("✅ File successfully saved to: " + file.getAbsolutePath()); 
                        } catch (Exception e) { 
                            System.out.println("❌ Error saving file: " + e.getMessage()); 
                        }
                    }
                }
            }
        } catch (Exception e) { 
            System.out.println("\n❌ Client Error: " + e.getMessage()); 
        } finally { 
            scanner.close(); 
        }
    }

    private static ReelResponse sendRequest(ReelRequest request) throws Exception {
        try (Socket socket = new Socket("localhost", 5000);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.writeObject(request); 
            out.flush(); 
            return (ReelResponse) in.readObject();
        }
    }
}