package edu.utem.ftmk.llm;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                                    boolean ok = processOneTranscript(request, dbRepo, llmService, responsePacket);
                                    responsePacket.status = ok ? "Success" : "Failed";
                                    if (ok) {
                                        responsePacket.logMessage = "[ID " + request.reelId + "] OK " +
                                            request.promptTechnique + " | " + request.modelName + " | JSON saved";
                                    } else {
                                        responsePacket.logMessage = "[ID " + request.reelId + "] FAILED";
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

                                case "RUN_BATCH_ALL": {
                                    System.out.println("\n=> RUN_BATCH_ALL | Model: " + request.modelName + " | Tech: " + request.promptTechnique);
                                    dbRepo.insertAllPendingExperiments(request.modelName, request.promptTechnique);
                                    List<Integer> ids = dbRepo.getAllTranscriptIds();
                                    int success = 0, failed = 0;
                                    for (int id : ids) {
                                        ReelRequest singleReq = new ReelRequest(id, request.modelName, request.promptTechnique);
                                        ReelResponse tempResp = new ReelResponse();
                                        boolean ok = processOneTranscript(singleReq, dbRepo, llmService, tempResp);
                                        if (ok) success++; else failed++;
                                        System.out.println("[ID " + id + "] " + (ok ? "OK" : "FAILED") +
                                            " (" + (success + failed) + "/" + ids.size() + ")");
                                    }
                                    responsePacket.batchTotal   = ids.size();
                                    responsePacket.batchSuccess = success;
                                    responsePacket.batchFailed  = failed;
                                    break;
                                }

                                case "STATUS_MATRIX": {
                                    System.out.println("\n=> STATUS_MATRIX | Technique: " + request.promptTechnique);
                                    responsePacket.matrixRows = dbRepo.getStatusMatrix(request.promptTechnique);
                                    break;
                                }

                                case "TRANSCRIPT_COUNT": {
                                    System.out.println("\n=> TRANSCRIPT_COUNT");
                                    responsePacket.totalTranscripts = dbRepo.getTotalTranscriptCount();
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

    private static boolean processOneTranscript(ReelRequest request, DatabaseRepository dbRepo,
                                                 LLMService llmService, ReelResponse responsePacket) {
        int experimentId = dbRepo.getPendingExperimentId(
            request.reelId, request.modelName, request.promptTechnique);
        if (experimentId == -1) {
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
            try {
                responsePacket.jsonOutput = llmService.prompt(
                    request.modelName, systemPrompt + "\n\n" + finalUserPrompt);
            } catch (Exception ollamaEx) {
                System.out.println("=> Ollama failed for ID " + request.reelId + ": " + ollamaEx.getMessage());
                dbRepo.updateExperimentStatus(experimentId, "failed");
                throw ollamaEx;
            }

            System.out.println("=> Saving to DB...");
            dbRepo.saveExperimentResultsWithId(
                experimentId, request.reelId, request.modelName,
                request.promptTechnique, responsePacket.jsonOutput);

            dbRepo.updateExperimentStatus(experimentId, "completed");
            System.out.println("=> Experiment #" + experimentId + " status: completed");
            return true;

        } catch (Exception analyzeEx) {
            dbRepo.updateExperimentStatus(experimentId, "failed");
            System.out.println("=> Experiment #" + experimentId + " status: failed — " + analyzeEx.getMessage());
            return false;
        }
    }
}