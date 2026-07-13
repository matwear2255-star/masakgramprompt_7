package edu.utem.ftmk.llm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class DatabaseRepository {

    public void loadReelMetadata(ReelResponse response, int transcriptId) throws Exception {
        String sql = "SELECT t.file_path AS trans_path, a.file_path AS audio_path, i.instagram_account " +
                     "FROM transcript t JOIN reel r ON t.reel_id = r.reel_id " +
                     "JOIN influencer i ON r.influencer_id = i.influencer_id " +
                     "JOIN audio_file a ON t.audio_id = a.audio_id WHERE t.transcript_id = ?";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transcriptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    response.transcriptFilePath = rs.getString("trans_path");
                    response.audioFilePath = rs.getString("audio_path");
                    response.influencerHandle = rs.getString("instagram_account");
                } else throw new Exception("Transcript ID " + transcriptId + " not found in DB.");
            }
        }
        String gtSql = "SELECT COUNT(*) FROM ground_truth_reel WHERE transcript_id = ?";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(gtSql)) {
            ps.setInt(1, transcriptId);
            try (ResultSet rs = ps.executeQuery()) { response.groundTruthAvailable = (rs.next() && rs.getInt(1) > 0); }
        }
    }

    public void saveExperimentResults(int transcriptId, String modelTag, String techniqueName, String rawJson) {
        String dbTechniqueName = techniqueName.replace("_", "-");
        try (Connection conn = DatabaseConnection.getConnection()) {
            int modelId = -1, techniqueId = -1;
            try (PreparedStatement psM = conn.prepareStatement("SELECT model_id FROM llm_model WHERE model_tag = ?")) {
                psM.setString(1, modelTag); ResultSet rsM = psM.executeQuery(); if (rsM.next()) modelId = rsM.getInt("model_id");
            }
            try (PreparedStatement psT = conn.prepareStatement("SELECT technique_id FROM prompt_technique WHERE technique_name = ?")) {
                psT.setString(1, dbTechniqueName); ResultSet rsT = psT.executeQuery(); if (rsT.next()) techniqueId = rsT.getInt("technique_id");
            }
            if (modelId == -1 || techniqueId == -1) {
                System.out.println("model_id or technique_id not found. modelTag=" + modelTag + " technique=" + dbTechniqueName);
                return;
            }
            int experimentId = -1;
            String insertExp = "INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status) VALUES (?, ?, ?, 0, 'completed')";
            try (PreparedStatement ps = conn.prepareStatement(insertExp, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, transcriptId); ps.setInt(2, modelId); ps.setInt(3, techniqueId); ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys(); if (rs.next()) experimentId = rs.getInt(1);
            }
            saveNutritionData(conn, experimentId, transcriptId, rawJson);
        } catch (Exception e) { System.out.println("DB Save Error: " + e.getMessage()); e.printStackTrace(); }
    }

    public int insertRunningExperiment(int transcriptId, String modelTag, String techniqueName) {
        String dbTech = techniqueName.replace("_", "-");
        try (Connection conn = DatabaseConnection.getConnection()) {
            int modelId = -1, techniqueId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT model_id FROM llm_model WHERE model_tag = ?")) {
                ps.setString(1, modelTag); ResultSet rs = ps.executeQuery();
                if (rs.next()) modelId = rs.getInt("model_id");
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT technique_id FROM prompt_technique WHERE technique_name = ?")) {
                ps.setString(1, dbTech); ResultSet rs = ps.executeQuery();
                if (rs.next()) techniqueId = rs.getInt("technique_id");
            }
            if (modelId == -1 || techniqueId == -1) return -1;
            String sql = "INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status) VALUES (?, ?, ?, 0, 'running')";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, transcriptId); ps.setInt(2, modelId); ps.setInt(3, techniqueId);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) { System.out.println("insertRunningExperiment error: " + e.getMessage()); }
        return -1;
    }

    public void updateExperimentStatus(int experimentId, String status) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE experiment SET status = ?, executed_at = NOW() WHERE experiment_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, experimentId);
            ps.executeUpdate();
        } catch (Exception e) { System.out.println("updateExperimentStatus error: " + e.getMessage()); }
    }

    public void saveExperimentResultsWithId(int experimentId, int transcriptId, String modelTag, String techniqueName, String rawJson) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            saveNutritionData(conn, experimentId, transcriptId, rawJson);
        } catch (Exception e) { System.out.println("saveExperimentResultsWithId error: " + e.getMessage()); e.printStackTrace(); }
    }

    // Load ground truth ingredient name_en list for a transcript
    private List<String> loadGroundTruthNames(Connection conn, int transcriptId) throws Exception {
        List<String> names = new ArrayList<>();
        String sql = "SELECT LOWER(gi.name_en) FROM ground_truth_ingredient gi " +
                     "JOIN ground_truth_reel gr ON gi.gt_reel_id = gr.gt_reel_id " +
                     "WHERE gr.transcript_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transcriptId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String n = rs.getString(1);
                if (n != null) names.add(n.trim());
            }
        }
        return names;
    }

    // Check if llm ingredient name_en matches any ground truth name (partial/contains match)
    private boolean isHallucinated(String llmNameEn, List<String> gtNames) {
        if (llmNameEn == null || llmNameEn.isEmpty() || gtNames.isEmpty()) return true;
        String llmLower = llmNameEn.toLowerCase().trim();
        for (String gt : gtNames) {
            if (gt.contains(llmLower) || llmLower.contains(gt)) return false;
        }
        return true;
    }

    // Shared nutrition save logic
    private void saveNutritionData(Connection conn, int experimentId, int transcriptId, String rawJson) throws Exception {
        String cleanJsonStr = extractJsonObject(rawJson);
        System.out.println("=> Extracted JSON (" + cleanJsonStr.length() + " chars): " + cleanJsonStr.substring(0, Math.min(200, cleanJsonStr.length())) + (cleanJsonStr.length() > 200 ? "..." : ""));

        boolean jsonValid = true;
        JSONObject rootJson = null;
        try { rootJson = new JSONObject(cleanJsonStr); } catch (Exception e) { jsonValid = false; }

        if (rootJson != null) System.out.println("DEBUG rootJson keys: " + rootJson.keySet().toString());

        JSONObject t = jsonValid ? resolveNutritionTotal(rootJson) : null;
        JSONObject s = jsonValid ? resolveAmountPerServing(rootJson) : null;
        System.out.println("DEBUG s=" + (s == null ? "NULL" : "calories=" + s.opt("calories")));
        System.out.println("DEBUG t=" + (t == null ? "NULL" : "calories=" + t.opt("calories")));

        int resultId = -1;
        String insertNutri = "INSERT INTO nutrition_result (" +
            "experiment_id, recipe_name, servings_estimated, " +
            "serving_calories, serving_total_fat_g, serving_saturated_fat_g, serving_cholesterol_mg, " +
            "serving_sodium_mg, serving_carbohydrate_g, serving_fiber_g, serving_sugars_g, " +
            "serving_protein_g, serving_vitamin_d_mcg, serving_calcium_mg, serving_iron_mg, serving_potassium_mg, " +
            "total_calories, total_fat_g, total_saturated_fat_g, total_cholesterol_mg, " +
            "total_sodium_mg, total_carbohydrate_g, total_fiber_g, total_sugars_g, " +
            "total_protein_g, total_vitamin_d_mcg, total_calcium_mg, total_iron_mg, total_potassium_mg, " +
            "json_valid, raw_json_output) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertNutri, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, experimentId);
            ps.setString(2, jsonValid ? rootJson.optString("recipe_name", null) : null);
            ps.setInt(3, jsonValid ? rootJson.optInt("servings_estimated", 0) : 0);
            if (s != null) {
                ps.setDouble(4,  s.optDouble("calories", 0.0));
                ps.setDouble(5,  s.optDouble("total_fat_g", 0.0));
                ps.setDouble(6,  s.optDouble("saturated_fat_g", 0.0));
                ps.setDouble(7,  s.optDouble("cholesterol_mg", 0.0));
                ps.setDouble(8,  s.optDouble("sodium_mg", 0.0));
                ps.setDouble(9,  s.optDouble("total_carbohydrate_g", 0.0));
                ps.setDouble(10, s.optDouble("dietary_fiber_g", 0.0));
                ps.setDouble(11, s.optDouble("total_sugars_g", 0.0));
                ps.setDouble(12, s.optDouble("protein_g", 0.0));
                ps.setDouble(13, s.optDouble("vitamin_d_mcg", 0.0));
                ps.setDouble(14, s.optDouble("calcium_mg", 0.0));
                ps.setDouble(15, s.optDouble("iron_mg", 0.0));
                ps.setDouble(16, s.optDouble("potassium_mg", 0.0));
            } else { for (int i = 4; i <= 16; i++) ps.setDouble(i, 0.0); }
            if (t != null) {
                ps.setDouble(17, t.optDouble("calories", 0.0));
                ps.setDouble(18, t.optDouble("total_fat_g", 0.0));
                ps.setDouble(19, t.optDouble("saturated_fat_g", 0.0));
                ps.setDouble(20, t.optDouble("cholesterol_mg", 0.0));
                ps.setDouble(21, t.optDouble("sodium_mg", 0.0));
                ps.setDouble(22, t.optDouble("total_carbohydrate_g", 0.0));
                ps.setDouble(23, t.optDouble("dietary_fiber_g", 0.0));
                ps.setDouble(24, t.optDouble("total_sugars_g", 0.0));
                ps.setDouble(25, t.optDouble("protein_g", 0.0));
                ps.setDouble(26, t.optDouble("vitamin_d_mcg", 0.0));
                ps.setDouble(27, t.optDouble("calcium_mg", 0.0));
                ps.setDouble(28, t.optDouble("iron_mg", 0.0));
                ps.setDouble(29, t.optDouble("potassium_mg", 0.0));
            } else { for (int i = 17; i <= 29; i++) ps.setDouble(i, 0.0); }
            ps.setBoolean(30, jsonValid);
            ps.setString(31, cleanJsonStr);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys(); if (rs.next()) resultId = rs.getInt(1);
        }

        if (jsonValid && rootJson.has("ingredients")) {
            // Load ground truth names for this transcript for hallucination check
            List<String> gtNames = new ArrayList<>();
            try { gtNames = loadGroundTruthNames(conn, transcriptId); }
            catch (Exception e) { System.out.println("Warning: could not load GT names for hallucination check: " + e.getMessage()); }

            JSONArray arr = rootJson.getJSONArray("ingredients");
            String insertIng = "INSERT INTO ingredient_result (result_id, name_original, name_en, quantity_value, unit_original, unit_en, estimated_weight_g, calories, total_fat_g, saturated_fat_g, cholesterol_mg, sodium_mg, total_carbohydrate_g, dietary_fiber_g, total_sugars_g, protein_g, vitamin_d_mcg, calcium_mg, iron_mg, potassium_mg, hallucination) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertIng)) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ing = arr.getJSONObject(i);
                    String nameEn = ing.optString("ingredient_name_en", "N/A");
                    boolean hallucinated = gtNames.isEmpty() ? false : isHallucinated(nameEn, gtNames);

                    ps.setInt(1, resultId);
                    ps.setString(2, ing.optString("ingredient_name_original", "N/A"));
                    ps.setString(3, nameEn);
                    Object qv = ing.opt("quantity_value");
                    ps.setDouble(4, qv instanceof Number ? ((Number)qv).doubleValue() : 0.0);
                    ps.setString(5, ing.optString("quantity_unit_original", ""));
                    ps.setString(6, ing.optString("quantity_unit_en", ""));
                    Object wg = ing.opt("estimated_weight_g");
                    if (wg instanceof Number) { ps.setDouble(7, ((Number)wg).doubleValue()); }
                    else { try { ps.setDouble(7, Double.parseDouble(wg.toString().replaceAll("[^0-9.]", ""))); } catch (Exception ex) { ps.setDouble(7, 0.0); } }
                    ps.setDouble(8,  ing.optDouble("calories", 0.0));
                    ps.setDouble(9,  ing.optDouble("total_fat_g", 0.0));
                    ps.setDouble(10, ing.optDouble("saturated_fat_g", 0.0));
                    ps.setDouble(11, ing.optDouble("cholesterol_mg", 0.0));
                    ps.setDouble(12, ing.optDouble("sodium_mg", 0.0));
                    ps.setDouble(13, ing.optDouble("total_carbohydrate_g", 0.0));
                    ps.setDouble(14, ing.optDouble("dietary_fiber_g", 0.0));
                    ps.setDouble(15, ing.optDouble("total_sugars_g", 0.0));
                    ps.setDouble(16, ing.optDouble("protein_g", 0.0));
                    ps.setDouble(17, ing.optDouble("vitamin_d_mcg", 0.0));
                    ps.setDouble(18, ing.optDouble("calcium_mg", 0.0));
                    ps.setDouble(19, ing.optDouble("iron_mg", 0.0));
                    ps.setDouble(20, ing.optDouble("potassium_mg", 0.0));
                    ps.setInt(21, hallucinated ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        System.out.println("Saved result #" + resultId + " for experiment #" + experimentId +
            " | ingredients: " + (jsonValid && rootJson.has("ingredients") ? rootJson.getJSONArray("ingredients").length() : 0));
    }

    public void saveFailedExperiment(int transcriptId, String modelTag, String techniqueName) {
        String dbTech = techniqueName.replace("_", "-");
        try (Connection conn = DatabaseConnection.getConnection()) {
            int modelId = -1, techniqueId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT model_id FROM llm_model WHERE model_tag = ?")) {
                ps.setString(1, modelTag); ResultSet rs = ps.executeQuery();
                if (rs.next()) modelId = rs.getInt("model_id");
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT technique_id FROM prompt_technique WHERE technique_name = ?")) {
                ps.setString(1, dbTech); ResultSet rs = ps.executeQuery();
                if (rs.next()) techniqueId = rs.getInt("technique_id");
            }
            if (modelId == -1 || techniqueId == -1) return;
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status) VALUES (?, ?, ?, 0, 'failed')")) {
                ps.setInt(1, transcriptId); ps.setInt(2, modelId); ps.setInt(3, techniqueId);
                ps.executeUpdate();
                System.out.println("Saved failed experiment for transcript " + transcriptId);
            }
        } catch (Exception e) { System.out.println("Could not save failed experiment: " + e.getMessage()); }
    }

    public int getAudioDuration(int transcriptId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT a.file_size_bytes FROM audio_file a " +
                 "JOIN transcript t ON a.audio_id = t.audio_id " +
                 "WHERE t.transcript_id = ?")) {
            ps.setInt(1, transcriptId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long fileSize = rs.getLong("file_size_bytes");
                if (fileSize > 0) return (int)(fileSize / 16000);
            }
        } catch (Exception e) { System.out.println("getAudioDuration error: " + e.getMessage()); }
        return 0;
    }
    
    public int getTotalTranscriptCount() {
        String sql = "SELECT COUNT(*) FROM transcript";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            System.out.println("getTotalTranscriptCount error: " + e.getMessage());
        }
        return 0;
    }

    public String generateFactSheetComparison(int transcriptId, String modelTag, String techniqueName) {
        StringBuilder sb = new StringBuilder();
        String dbTech = techniqueName.replace("_", "-");
        String expSql = "SELECT e.experiment_id, nr.result_id, nr.total_calories, nr.serving_calories, nr.servings_estimated " +
                        "FROM experiment e JOIN llm_model m ON e.model_id = m.model_id " +
                        "JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                        "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id " +
                        "WHERE e.transcript_id = ? AND m.model_tag = ? AND pt.technique_name = ? " +
                        "ORDER BY e.experiment_id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(expSql)) {
            ps.setInt(1, transcriptId); ps.setString(2, modelTag); ps.setString(3, dbTech);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return "Error: No run found for this configuration.";
            int resultId = rs.getInt("result_id");
            double totalCal = rs.getDouble("total_calories");
            double servingCal = rs.getDouble("serving_calories");
            double displayCal = totalCal > 0 ? totalCal : servingCal;
            sb.append(String.format("%-40s | %-40s\n", "HUMAN GROUND TRUTH", "LLM PREDICTION (" + modelTag + ")"));
            sb.append("-----------------------------------------|-----------------------------------------\n");
            sb.append(String.format("%-40s | Total Calories  : %.2f kcal\n", "See DB", displayCal));
            sb.append(String.format("%-40s | Per Serving     : %.2f kcal (x%d servings)\n", "", rs.getDouble("serving_calories"), rs.getInt("servings_estimated")));
            sb.append("-----------------------------------------|-----------------------------------------\n");
            try (PreparedStatement psIng = conn.prepareStatement("SELECT name_en, estimated_weight_g, calories FROM ingredient_result WHERE result_id = ?")) {
                psIng.setInt(1, resultId); ResultSet rsIng = psIng.executeQuery();
                while (rsIng.next()) {
                    sb.append(String.format("%-40s | %s (%.1fg) - %.1f kcal\n", "[Data in DB]", rsIng.getString("name_en"), rsIng.getDouble("estimated_weight_g"), rsIng.getDouble("calories")));
                }
            }
        } catch (Exception e) { return "DB Error: " + e.getMessage(); }
        return sb.toString();
    }

    private String extractJsonObject(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.replaceAll("(?s)```(?:json)?\\s*", "").trim();
        int start = cleaned.indexOf('{');
        if (start < 0) return cleaned;
        int depth = 0;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return cleaned.substring(start, i + 1); }
        }
        int end = cleaned.lastIndexOf('}');
        return (end > start) ? cleaned.substring(start, end + 1) : cleaned;
    }

    private JSONObject resolveNutritionTotal(JSONObject root) {
        String[] aliases = {
            "nutrition_total", "nutritional_totals", "nutritional_total",
            "total_nutrition", "totals", "nutrition_totals",
            "total_nutritional_value", "nutrition_summary"
        };
        for (String key : aliases) {
            JSONObject obj = root.optJSONObject(key);
            if (obj != null) return obj;
        }
        if (root.has("ingredients")) {
            JSONArray arr = root.getJSONArray("ingredients");
            JSONObject sum = new JSONObject();
            String[] fields = {"calories","total_fat_g","saturated_fat_g","cholesterol_mg",
                "sodium_mg","total_carbohydrate_g","dietary_fiber_g","total_sugars_g",
                "protein_g","vitamin_d_mcg","calcium_mg","iron_mg","potassium_mg"};
            for (String f : fields) sum.put(f, 0.0);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ing = arr.getJSONObject(i);
                for (String f : fields)
                    sum.put(f, sum.optDouble(f, 0.0) + ing.optDouble(f, 0.0));
            }
            return sum;
        }
        if (root.has("calories") || root.has("total_calories")) {
            JSONObject synthetic = new JSONObject();
            synthetic.put("calories",             root.optDouble("calories", root.optDouble("total_calories", 0.0)));
            synthetic.put("total_fat_g",          root.optDouble("total_fat_g", 0.0));
            synthetic.put("saturated_fat_g",      root.optDouble("saturated_fat_g", 0.0));
            synthetic.put("cholesterol_mg",       root.optDouble("cholesterol_mg", 0.0));
            synthetic.put("sodium_mg",            root.optDouble("sodium_mg", 0.0));
            synthetic.put("total_carbohydrate_g", root.optDouble("total_carbohydrate_g", 0.0));
            synthetic.put("dietary_fiber_g",      root.optDouble("dietary_fiber_g", 0.0));
            synthetic.put("total_sugars_g",       root.optDouble("total_sugars_g", 0.0));
            synthetic.put("protein_g",            root.optDouble("protein_g", 0.0));
            synthetic.put("vitamin_d_mcg",        root.optDouble("vitamin_d_mcg", 0.0));
            synthetic.put("calcium_mg",           root.optDouble("calcium_mg", 0.0));
            synthetic.put("iron_mg",              root.optDouble("iron_mg", 0.0));
            synthetic.put("potassium_mg",         root.optDouble("potassium_mg", 0.0));
            return synthetic;
        }
        return null;
    }

    private JSONObject resolveAmountPerServing(JSONObject root) {
        String[] aliases = {
            "amount_per_serving", "per_serving", "serving", "nutrition_per_serving",
            "per_serving_nutrition", "serving_nutrition", "nutritional_info_per_serving"
        };
        for (String key : aliases) {
            JSONObject obj = root.optJSONObject(key);
            if (obj != null) return obj;
        }
        return null;
    }

    public String exportLayerToCSV(String layerCode) {
        String sql = "";
        switch (layerCode) {
            case "LAYER_1A": sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, gi.name_original AS gt_name_original, gi.name_en AS gt_name_en FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id JOIN ingredient_result ir ON nr.result_id = ir.result_id LEFT JOIN ground_truth_reel gr ON e.transcript_id = gr.transcript_id LEFT JOIN ground_truth_ingredient gi ON gr.gt_reel_id = gi.gt_reel_id WHERE e.status = 'completed'"; break;
            case "LAYER_1B": sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, gi.name_original AS gt_name_original, gi.name_en AS gt_name_en FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id JOIN ingredient_result ir ON nr.result_id = ir.result_id LEFT JOIN ground_truth_reel gr ON e.transcript_id = gr.transcript_id LEFT JOIN ground_truth_ingredient gi ON gr.gt_reel_id = gi.gt_reel_id WHERE e.status = 'completed'"; break;
            case "LAYER_2A": sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, ir.name_en AS pred_name, ir.quantity_value AS pred_qty, ir.estimated_weight_g AS pred_weight_g, gi.quantity_value_culinary AS gt_qty, gi.estimated_weight_g AS gt_weight_g FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id JOIN ingredient_result ir ON nr.result_id = ir.result_id LEFT JOIN ground_truth_reel gr ON e.transcript_id = gr.transcript_id LEFT JOIN ground_truth_ingredient gi ON gr.gt_reel_id = gi.gt_reel_id WHERE e.status = 'completed'"; break;
            case "LAYER_2B": sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, ir.name_en AS pred_name, ir.calories AS pred_calories, ir.protein_g AS pred_protein, ir.total_fat_g AS pred_fat, ir.total_carbohydrate_g AS pred_carbs, gi.calories AS gt_calories, gi.protein_g AS gt_protein, gi.total_fat_g AS gt_fat, gi.total_carbohydrate_g AS gt_carbs FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id JOIN ingredient_result ir ON nr.result_id = ir.result_id LEFT JOIN ground_truth_reel gr ON e.transcript_id = gr.transcript_id LEFT JOIN ground_truth_ingredient gi ON gr.gt_reel_id = gi.gt_reel_id WHERE e.status = 'completed'"; break;
            case "LAYER_2C": sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, nr.total_calories, nr.total_protein_g, nr.total_fat_g, nr.total_carbohydrate_g, nr.serving_calories, nr.servings_estimated FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id WHERE e.status = 'completed'"; break;
            case "LAYER_3A": sql = "SELECT m.model_name, pt.technique_name, COUNT(*) AS total_runs, SUM(CASE WHEN nr.json_valid = TRUE THEN 1 ELSE 0 END) AS valid_count, ROUND(SUM(CASE WHEN nr.json_valid = TRUE THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS valid_pct FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id WHERE e.status = 'completed' GROUP BY m.model_name, pt.technique_name"; break;
            case "LAYER_3B": sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, CASE WHEN ir.hallucination = 1 THEN 'YES' ELSE 'NO' END AS hallucination_flag FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id JOIN ingredient_result ir ON nr.result_id = ir.result_id WHERE e.status = 'completed'"; break;
            case "LAYER_3C": sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, COUNT(ir.ingredient_id) AS pred_ingredient_count, (SELECT COUNT(*) FROM ground_truth_reel gr2 JOIN ground_truth_ingredient gi2 ON gr2.gt_reel_id = gi2.gt_reel_id WHERE gr2.transcript_id = e.transcript_id) AS gt_ingredient_count FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id JOIN ingredient_result ir ON nr.result_id = ir.result_id WHERE e.status = 'completed' GROUP BY e.experiment_id, e.transcript_id, m.model_name, pt.technique_name"; break;
            case "LAYER_4":  sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, ir.name_en AS llm_ingredient, '' AS human_evaluator_score, '' AS human_evaluator_comments FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id JOIN ingredient_result ir ON nr.result_id = ir.result_id WHERE e.status = 'completed'"; break;
            case "LAYER_5":  sql = "SELECT e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, nr.json_valid, COUNT(DISTINCT ir.ingredient_id) AS pred_count, nr.total_calories, nr.serving_calories, nr.servings_estimated FROM experiment e JOIN llm_model m ON e.model_id = m.model_id JOIN prompt_technique pt ON e.technique_id = pt.technique_id JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id LEFT JOIN ingredient_result ir ON nr.result_id = ir.result_id WHERE e.status = 'completed' GROUP BY e.experiment_id, e.transcript_id, m.model_name, pt.technique_name, nr.json_valid, nr.total_calories, nr.serving_calories, nr.servings_estimated"; break;
            default: return "Invalid Export Layer Selected!";
        }
        StringBuilder csv = new StringBuilder();
        try (Connection conn = DatabaseConnection.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            for (int i = 1; i <= colCount; i++) csv.append(meta.getColumnName(i)).append(i == colCount ? "" : ",");
            csv.append("\n");
            while (rs.next()) {
                for (int i = 1; i <= colCount; i++) {
                    String val = rs.getString(i);
                    if (val != null) csv.append("\"").append(val.replace("\"", "\"\"")).append("\"");
                    csv.append(i == colCount ? "" : ",");
                }
                csv.append("\n");
            }
        } catch (Exception e) { return "Export failed: " + e.getMessage(); }
        return csv.toString();
    }

    public java.util.List<Object[]> getStatusMatrix(String techniqueName) {
        String dbTech = techniqueName.replace("_", "-");
        String[] models = {
            "llama3.2:3b", "phi4-mini", "qwen2.5:3b",
            "aisingapore/Gemma-SEA-LION-v4-4B-VL", "medgemma:4b"
        };
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        java.util.Map<Integer, Object[]> rowMap = new java.util.LinkedHashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT t.transcript_id, t.verified_by_name, " +
                "       m.model_tag, e.experiment_id, e.status " +
                "FROM transcript t " +
                "LEFT JOIN experiment e ON t.transcript_id = e.transcript_id " +
                "LEFT JOIN llm_model m ON e.model_id = m.model_id " +
                "LEFT JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                "WHERE pt.technique_name = ? OR pt.technique_name IS NULL " +
                "ORDER BY t.transcript_id, m.model_tag, " +
                "FIELD(e.status,'completed','running','failed','pending') ASC")) {
            ps.setString(1, dbTech);
            ResultSet rs = ps.executeQuery();
            java.util.Map<String, Integer> priority = new java.util.HashMap<>();
            priority.put("completed", 1); priority.put("running", 2);
            priority.put("failed", 3); priority.put("pending", 4);
            while (rs.next()) {
                int tid       = rs.getInt("transcript_id");
                String vname  = rs.getString("verified_by_name");
                String mtag   = rs.getString("model_tag");
                int expId     = rs.getInt("experiment_id");
                String status = rs.getString("status");
                System.out.println("DEBUG MATRIX | tid=" + tid + " model=" + mtag + " status=" + status + " expId=" + expId);
                if (!rowMap.containsKey(tid)) {
                    Object[] cells = new Object[7];
                    cells[0] = tid;
                    cells[1] = vname != null ? vname : "-";
                    for (int i = 2; i < 7; i++) cells[i] = "-";
                    rowMap.put(tid, cells);
                }
                if (mtag != null && status != null) {
                    for (int i = 0; i < models.length; i++) {
                        if (models[i].equals(mtag)) {
                            String newLabel = status.toUpperCase() + " (#" + expId + ")";
                            String existing = rowMap.get(tid)[i + 2] != null ? rowMap.get(tid)[i + 2].toString() : "-";
                            if (existing.equals("-")) {
                                rowMap.get(tid)[i + 2] = newLabel;
                            } else {
                                String existingStatus = existing.split(" ")[0].toLowerCase();
                                int existPri = priority.getOrDefault(existingStatus, 99);
                                int newPri   = priority.getOrDefault(status, 99);
                                if (newPri < existPri) rowMap.get(tid)[i + 2] = newLabel;
                            }
                        }
                    }
                }
            }
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT transcript_id, verified_by_name FROM transcript ORDER BY transcript_id")) {
                ResultSet rs2 = ps2.executeQuery();
                while (rs2.next()) {
                    int tid = rs2.getInt("transcript_id");
                    if (!rowMap.containsKey(tid)) {
                        Object[] cells = new Object[7];
                        cells[0] = tid;
                        cells[1] = rs2.getString("verified_by_name") != null ? rs2.getString("verified_by_name") : "-";
                        for (int i = 2; i < 7; i++) cells[i] = "-";
                        rowMap.put(tid, cells);
                    }
                }
            }
        } catch (Exception e) { System.out.println("Matrix query error: " + e.getMessage()); }
        rows.addAll(rowMap.values());
        return rows;
    }

    public List<Object[]> getReelStatusRows(int transcriptId) {
        String[] models = {"llama3.2:3b","phi4-mini","qwen2.5:3b",
            "aisingapore/Gemma-SEA-LION-v4-4B-VL","medgemma:4b"};
        String[] techNames = {"zero-shot","few-shot","chain-of-thought","structured-output"};
        String[] techDisplay = {"zero_shot","few_shot","chain_of_thought","structured_output"};
        List<Object[]> rows = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (int t = 0; t < techNames.length; t++) {
                Object[] row = new Object[models.length + 1];
                row[0] = techDisplay[t];
                for (int m = 0; m < models.length; m++) {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT e.experiment_id, e.status FROM experiment e " +
                        "JOIN llm_model lm ON e.model_id = lm.model_id " +
                        "JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                        "WHERE e.transcript_id = ? AND lm.model_tag = ? AND pt.technique_name = ? " +
                        "ORDER BY e.experiment_id DESC LIMIT 1")) {
                        ps.setInt(1, transcriptId); ps.setString(2, models[m]); ps.setString(3, techNames[t]);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            row[m + 1] = capitalize(rs.getString("status")) + " #" + rs.getInt("experiment_id");
                        } else { row[m + 1] = "- Unexecuted"; }
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) { System.out.println("getReelStatusRows error: " + e.getMessage()); }
        return rows;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    public String exportFactSheetCsv(int transcriptId, String modelTag, String techniqueName) {
        String dbTech = techniqueName.replace("_", "-");
        StringBuilder csv = new StringBuilder();
        csv.append("ingredient_name_original,ingredient_name_en,quantity_value,unit_en,estimated_weight_g,calories,total_fat_g,protein_g,total_carbohydrate_g,sodium_mg,hallucination\n");
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT ir.* FROM ingredient_result ir " +
                "JOIN nutrition_result nr ON ir.result_id = nr.result_id " +
                "JOIN experiment e ON nr.experiment_id = e.experiment_id " +
                "JOIN llm_model m ON e.model_id = m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                "WHERE e.transcript_id = ? AND m.model_tag = ? AND pt.technique_name = ? " +
                "ORDER BY e.experiment_id DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, transcriptId); ps.setString(2, modelTag); ps.setString(3, dbTech);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    csv.append("\"").append(rs.getString("name_original")).append("\",");
                    csv.append("\"").append(rs.getString("name_en")).append("\",");
                    csv.append(rs.getDouble("quantity_value")).append(",");
                    csv.append("\"").append(rs.getString("unit_en")).append("\",");
                    csv.append(rs.getDouble("estimated_weight_g")).append(",");
                    csv.append(rs.getDouble("calories")).append(",");
                    csv.append(rs.getDouble("total_fat_g")).append(",");
                    csv.append(rs.getDouble("protein_g")).append(",");
                    csv.append(rs.getDouble("total_carbohydrate_g")).append(",");
                    csv.append(rs.getDouble("sodium_mg")).append(",");
                    int hall = rs.getInt("hallucination");
                    csv.append(rs.wasNull() ? "N/A" : (hall == 1 ? "Yes" : "No")).append("\n");
                }
            }
        } catch (Exception e) { return "Error: " + e.getMessage(); }
        return csv.toString();
    }

    public void loadFactSheetData(ReelResponse response, int transcriptId, String modelTag, String techniqueName) {
        String dbTech = techniqueName.replace("_", "-");
        List<Object[]> gtRows  = new ArrayList<>();
        List<Object[]> llmRows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String gtSql = "SELECT gi.name_original, gi.name_en, gi.quantity_expression, " +
                "gi.quantity_unit_culinary, gi.estimated_weight_g, gi.calories, " +
                "gi.protein_g, gi.total_fat_g, gi.total_carbohydrate_g, gi.sodium_mg, gi.language_mentioned " +
                "FROM ground_truth_ingredient gi " +
                "JOIN ground_truth_reel gr ON gi.gt_reel_id = gr.gt_reel_id " +
                "WHERE gr.transcript_id = ? ORDER BY gi.gt_ingredient_id";
            try (PreparedStatement ps = conn.prepareStatement(gtSql)) {
                ps.setInt(1, transcriptId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    response.gtTotalCalories += rs.getDouble("calories");
                    gtRows.add(new Object[]{
                        rs.getString("name_original"),
                        rs.getString("name_en") != null ? rs.getString("name_en") : "-",
                        rs.getString("quantity_expression") != null ? rs.getString("quantity_expression") : "-",
                        rs.getString("quantity_unit_culinary") != null ? rs.getString("quantity_unit_culinary") : "-",
                        String.format("%.1f", rs.getDouble("estimated_weight_g")),
                        String.format("%.1f", rs.getDouble("calories")),
                        String.format("%.1f", rs.getDouble("protein_g")),
                        String.format("%.1f", rs.getDouble("total_fat_g")),
                        String.format("%.1f", rs.getDouble("total_carbohydrate_g")),
                        String.format("%.1f", rs.getDouble("sodium_mg")),
                        rs.getString("language_mentioned") != null ? rs.getString("language_mentioned") : "-"
                    });
                }
            }
            String expSql = "SELECT nr.result_id, nr.total_calories, nr.serving_calories, nr.servings_estimated " +
                "FROM experiment e JOIN llm_model m ON e.model_id = m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id " +
                "WHERE e.transcript_id = ? AND m.model_tag = ? AND pt.technique_name = ? " +
                "ORDER BY e.experiment_id DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(expSql)) {
                ps.setInt(1, transcriptId); ps.setString(2, modelTag); ps.setString(3, dbTech);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int resultId = rs.getInt("result_id");
                    response.llmTotalCalories   = rs.getDouble("total_calories");
                    response.llmServingCalories = rs.getDouble("serving_calories");
                    response.llmServings        = rs.getInt("servings_estimated");
                    if (response.llmTotalCalories == 0) response.llmTotalCalories = response.llmServingCalories;
                    try (PreparedStatement psI = conn.prepareStatement(
                        "SELECT name_original, name_en, estimated_weight_g, calories, " +
                        "protein_g, total_fat_g, total_carbohydrate_g, sodium_mg, hallucination " +
                        "FROM ingredient_result WHERE result_id = ?")) {
                        psI.setInt(1, resultId);
                        ResultSet rsI = psI.executeQuery();
                        while (rsI.next()) {
                            int hall = rsI.getInt("hallucination");
                            String hallLabel = rsI.wasNull() ? "N/A" : (hall == 1 ? "Yes" : "No");
                            llmRows.add(new Object[]{
                                rsI.getString("name_original"),
                                rsI.getString("name_en") != null ? rsI.getString("name_en") : "-",
                                String.format("%.1f", rsI.getDouble("estimated_weight_g")),
                                String.format("%.1f", rsI.getDouble("calories")),
                                String.format("%.1f", rsI.getDouble("protein_g")),
                                String.format("%.1f", rsI.getDouble("total_fat_g")),
                                String.format("%.1f", rsI.getDouble("total_carbohydrate_g")),
                                String.format("%.1f", rsI.getDouble("sodium_mg")),
                                hallLabel
                            });
                        }
                    }
                }
            }
        } catch (Exception e) { System.out.println("loadFactSheetData error: " + e.getMessage()); }
        response.gtRows  = gtRows;
        response.llmRows = llmRows;
    }

    public void insertAllPendingExperiments(String modelTag, String techniqueName) {
        String dbTech = techniqueName.replace("_", "-");
        try (Connection conn = DatabaseConnection.getConnection()) {
            int modelId = -1, techniqueId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT model_id FROM llm_model WHERE model_tag = ?")) {
                ps.setString(1, modelTag); ResultSet rs = ps.executeQuery();
                if (rs.next()) modelId = rs.getInt("model_id");
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT technique_id FROM prompt_technique WHERE technique_name = ?")) {
                ps.setString(1, dbTech); ResultSet rs = ps.executeQuery();
                if (rs.next()) techniqueId = rs.getInt("technique_id");
            }
            if (modelId == -1 || techniqueId == -1) {
                System.out.println("insertAllPendingExperiments: model or technique not found");
                return;
            }
            List<Integer> transcriptIds = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT transcript_id FROM transcript ORDER BY transcript_id")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) transcriptIds.add(rs.getInt("transcript_id"));
            }
            String sql = "INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status) VALUES (?, ?, ?, 0, 'pending')";
            String checkSql = "SELECT COUNT(*) FROM experiment e " +
                "JOIN llm_model m2 ON e.model_id = m2.model_id " +
                "JOIN prompt_technique pt2 ON e.technique_id = pt2.technique_id " +
                "WHERE e.transcript_id = ? AND m2.model_tag = ? AND pt2.technique_name = ?";
            int insertedCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                for (int tid : transcriptIds) {
                    psCheck.setInt(1, tid); psCheck.setString(2, modelTag); psCheck.setString(3, dbTech);
                    ResultSet rs = psCheck.executeQuery();
                    if (rs.next() && rs.getInt(1) == 0) {
                        ps.setInt(1, tid); ps.setInt(2, modelId); ps.setInt(3, techniqueId);
                        ps.addBatch();
                        insertedCount++;
                    }
                }
                ps.executeBatch();
                System.out.println("Inserted " + insertedCount + " pending experiments for " + modelTag + " / " + dbTech);
            }
        } catch (Exception e) { System.out.println("insertAllPendingExperiments error: " + e.getMessage()); }
    }

    public int getPendingExperimentId(int transcriptId, String modelTag, String techniqueName) {
        String dbTech = techniqueName.replace("_", "-");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT e.experiment_id FROM experiment e " +
                "JOIN llm_model m ON e.model_id = m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id = pt.technique_id " +
                "WHERE e.transcript_id = ? AND m.model_tag = ? AND pt.technique_name = ? AND e.status = 'pending' " +
                "ORDER BY e.experiment_id DESC LIMIT 1")) {
            ps.setInt(1, transcriptId); ps.setString(2, modelTag); ps.setString(3, dbTech);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("experiment_id");
        } catch (Exception e) { System.out.println("getPendingExperimentId error: " + e.getMessage()); }
        return -1;
    }

}