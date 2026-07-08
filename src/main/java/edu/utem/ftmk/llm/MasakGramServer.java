package edu.utem.ftmk.llm;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;

public class MasakGramServer {

    public static void main(String[] args) {
        final int PORT = 5000;
        LLMService llmService = new LLMService();
        DatabaseRepository dbRepo = new DatabaseRepository();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("==================================================");
            System.out.println("[SERVER] MasakGram AI Engine is Live!");
            System.out.println("==================================================");

            while (true) {
                try (Socket socket = serverSocket.accept();
                     ObjectInputStream  input  = new ObjectInputStream(socket.getInputStream());
                     ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {

                    ReelRequest request = null;
                    try {
                        Object obj = input.readObject();
                        if (obj instanceof ReelRequest) {
                            request = (ReelRequest) obj;
                            ReelResponse responsePacket = new ReelResponse();
                            responsePacket.reelId = request.reelId;
                            responsePacket.status = "Success";

                            switch (request.action) {

                                case "ANALYZE": {
                                    System.out.println("\n=> ANALYZE | ID: " + request.reelId +
                                        " | Model: " + request.modelName +
                                        " | Tech: "  + request.promptTechnique);

                                    // Step 1: Find pending experiment and update to RUNNING
                                    int experimentId = dbRepo.getPendingExperimentId(
                                        request.reelId, request.modelName, request.promptTechnique);
                                    if (experimentId == -1) {
                                        // No pending row found, create running one directly
                                        experimentId = dbRepo.insertRunningExperiment(
                                            request.reelId, request.modelName, request.promptTechnique);
                                    } else {
                                        dbRepo.updateExperimentStatus(experimentId, "running");
                                    }
                                    System.out.println("=> Experiment #" + experimentId + " status: running");

                                    try {
                                        dbRepo.loadReelMetadata(responsePacket, request.reelId);

                                        String fullContent = Files.readString(
                                            Path.of(responsePacket.transcriptFilePath),
                                            java.nio.charset.StandardCharsets.UTF_8);

                                        responsePacket.languageTag      = "Malay";
                                        responsePacket.transcriptStatus = "SUCCESS";
                                        for (String line : fullContent.split("\n")) {
                                            if (line.startsWith("Language")) {
                                                String raw = line.split(":")[1].trim();
                                                responsePacket.languageTag =
                                                    (raw.equalsIgnoreCase("ms") || raw.equalsIgnoreCase("code-switched"))
                                                        ? "code-switched"
                                                        : raw.equalsIgnoreCase("en") ? "English" : "Malay";
                                            } else if (line.startsWith("Status")) {
                                                responsePacket.transcriptStatus = line.split(":")[1].trim();
                                            }
                                        }

                                        responsePacket.videoDuration = dbRepo.getAudioDuration(request.reelId);

                                        String transcriptText = fullContent.contains("=====================================")
                                            ? fullContent.split("=====================================")[1].trim()
                                            : fullContent;
                                        responsePacket.transcriptPreview = transcriptText;

                                        String systemPrompt       = Files.readString(Path.of("prompts/" + request.promptTechnique + "_system.txt"));
                                        String userPromptTemplate = Files.readString(Path.of("prompts/" + request.promptTechnique + "_user.txt"));
                                        String finalUserPrompt    = userPromptTemplate.replaceAll(
                                            "(?i)\\{\\{\\s*transcript\\s*\\}\\}",
                                            Matcher.quoteReplacement(transcriptText));

                                        System.out.println("=> Calling Ollama...");
                                        System.out.println("=> Calling Ollama...");
                                        try {
                                            responsePacket.jsonOutput = llmService.prompt(
                                                request.modelName, systemPrompt + "\n\n" + finalUserPrompt);
                                        } catch (Exception ollamaEx) {
                                            System.out.println("=> Ollama failed for ID " + request.reelId + ": " + ollamaEx.getMessage());
                                            dbRepo.updateExperimentStatus(experimentId, "failed");
                                            throw ollamaEx; // re-throw so the outer catch handles the response
                                        }

                                        System.out.println("=> Saving to DB...");
                                        // Step 2: Save results with existing experiment_id
                                        dbRepo.saveExperimentResultsWithId(
                                            experimentId, request.reelId, request.modelName,
                                            request.promptTechnique, responsePacket.jsonOutput);

                                        // Step 3: Update to COMPLETED
                                        dbRepo.updateExperimentStatus(experimentId, "completed");
                                        System.out.println("=> Experiment #" + experimentId + " status: completed");

                                        responsePacket.logMessage = "[ID " + request.reelId + "] OK " +
                                            request.promptTechnique + " | " + request.modelName + " | JSON saved";

                                    } catch (Exception analyzeEx) {
                                        // Update to FAILED if anything goes wrong
                                        dbRepo.updateExperimentStatus(experimentId, "failed");
                                        System.out.println("=> Experiment #" + experimentId + " status: failed — " + analyzeEx.getMessage());
                                        throw analyzeEx;
                                    }
                                    break;
                                }

                                case "FACT_SHEET": {
                                    System.out.println("\n=> FACT_SHEET | ID: " + request.reelId);
                                    responsePacket.factSheetView = dbRepo.generateFactSheetComparison(
                                        request.reelId, request.modelName, request.promptTechnique);
                                    break;
                                }

                                case "EXPORT": {
                                    System.out.println("\n=> EXPORT | Layer: " + request.exportLayer);
                                    responsePacket.csvPayload = dbRepo.exportLayerToCSV(request.exportLayer);
                                    break;
                                }

                                case "FACT_SHEET_DATA": {
                                    System.out.println("\n=> FACT_SHEET_DATA | ID: " + request.reelId);
                                    dbRepo.loadFactSheetData(responsePacket, request.reelId, request.modelName, request.promptTechnique);
                                    break;
                                }

                                case "REEL_DETAIL": {
                                    System.out.println("\n=> REEL_DETAIL | ID: " + request.reelId);
                                    dbRepo.loadReelMetadata(responsePacket, request.reelId);
                                    try {
                                        String fullContent = Files.readString(
                                            Path.of(responsePacket.transcriptFilePath),
                                            java.nio.charset.StandardCharsets.UTF_8);
                                        responsePacket.languageTag      = "Malay";
                                        responsePacket.transcriptStatus = "SUCCESS";
                                        for (String line : fullContent.split("\n")) {
                                            if (line.startsWith("Language")) {
                                                String raw = line.split(":")[1].trim();
                                                responsePacket.languageTag =
                                                    (raw.equalsIgnoreCase("ms") || raw.equalsIgnoreCase("code-switched"))
                                                        ? "code-switched"
                                                        : raw.equalsIgnoreCase("en") ? "English" : "Malay";
                                            } else if (line.startsWith("Status")) {
                                                responsePacket.transcriptStatus = line.split(":")[1].trim();
                                            }
                                        }
                                        responsePacket.videoDuration = dbRepo.getAudioDuration(request.reelId);
                                        String transcriptText = fullContent.contains("=====================================")
                                            ? fullContent.split("=====================================")[1].trim()
                                            : fullContent;
                                        responsePacket.transcriptPreview = transcriptText;
                                    } catch (Exception fileEx) {
                                        responsePacket.transcriptPreview = "Could not load transcript: " + fileEx.getMessage();
                                    }
                                    responsePacket.reelStatusRows = dbRepo.getReelStatusRows(request.reelId);
                                    break;
                                }

                                case "EXPORT_FACTSHEET": {
                                    System.out.println("\n=> EXPORT_FACTSHEET | ID: " + request.reelId);
                                    responsePacket.csvPayload = dbRepo.exportFactSheetCsv(
                                        request.reelId, request.modelName, request.promptTechnique);
                                    break;
                                }

                                case "PENDING_BATCH": {
                                    System.out.println("\n=> PENDING_BATCH | Model: " + request.modelName + " | Tech: " + request.promptTechnique);
                                    dbRepo.insertAllPendingExperiments(request.modelName, request.promptTechnique);
                                    break;
                                }

                                case "STATUS_MATRIX": {
                                    System.out.println("\n=> STATUS_MATRIX | Technique: " + request.promptTechnique);
                                    responsePacket.matrixRows = dbRepo.getStatusMatrix(request.promptTechnique);
                                    break;
                                }

                                default:
                                    System.out.println("Unknown action: " + request.action);
                            }

                            output.writeObject(responsePacket);
                            output.flush();
                        }

                    } catch (Exception taskEx) {
                        System.out.println("Task Error: " + taskEx.getMessage());
                        taskEx.printStackTrace();
                        ReelResponse err = new ReelResponse();
                        err.status     = "Failed";
                        err.jsonOutput = "[ERROR] " + taskEx.getMessage();
                        err.logMessage = request != null
                            ? "[ID " + request.reelId + "] FAILED: " + taskEx.getMessage()
                            : "[UNKNOWN] FAILED: " + taskEx.getMessage();
                        output.writeObject(err);
                        output.flush();
                    }

                } catch (Exception netEx) {
                    // connection closed — continue
                }
            }

        } catch (Exception e) { e.printStackTrace(); }
    }
}