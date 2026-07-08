package edu.utem.ftmk.llm;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
/**
* LLMService provides a reusable interface for sending prompts to locally
* hosted Large Language Models (LLMs) via Ollama.
*
* Ollama runs as a background server on the developer's machine and exposes
* a REST API on localhost:11434. This class uses LangChain4j's OllamaChatModel
* to communicate with that server.
*
* Supported models:
* - Llama 3.2 3B : General-purpose multilingual baseline
* - Phi-4-mini 3.8B : Efficient inference, good for batch processing
* - Qwen 2.5 3B : General multilingual, Malay-English code-switching
* - Gemma-SEA-LION 4B : Southeast Asian regional model, strong Malay
* - MedGemma 4B : Domain-specific biomedical and nutritional knowledge
*
* Usage example:
* LLMService service = new LLMService();
* String response = service.prompt(LLMService.LLAMA, "Your prompt here");
*/
public class LLMService {
 /**
 * The base URL where the Ollama server is running.
 * This is the default address when Ollama is installed locally.
 * Change this if Ollama is running on a different host or port.
 */
 private static final String OLLAMA_BASE_URL = "http://localhost:11434";
 /**
  * Model name constants for the four LLMs used in this study.
  * These strings must match exactly the model names registered in Ollama.
  * Use these constants instead of hardcoding model names in other classes.
  */
  public static final String LLAMA = "llama3.2:3b";
  public static final String PHI = "phi4-mini";
  public static final String QWEN = "qwen2.5:3b";
  public static final String SEALION = "aisingapore/Gemma-SEA-LION-v4-4B-VL";
  public static final String MEDGEMMA = "medgemma:4b";
  /**
  * Builds and returns a ChatModel instance connected to the specified
  * Ollama model. The model is configured with a 5-minute timeout to
  * accommodate slower CPU-based inference on local machines.
  *
  * @param modelName The Ollama model name (use the constants above)
   * @return A ChatModel ready to accept prompts
   */
   public ChatModel buildModel(String modelName) {
   return OllamaChatModel.builder()
   .baseUrl(OLLAMA_BASE_URL) // Connect to local Ollama server
   .modelName(modelName) // Select which LLM to use
   .timeout(Duration.ofMinutes(10)) // Allow up to 10 min for response
   .build();
   }
   /**
   * Sends a text prompt to the specified LLM and returns the response
   * as a plain string.
   *
   * This is the main method other classes will call to interact with
   * any of the four models in this study.
   *
    * @param modelName The Ollama model name (use the constants above)
    * @param userPrompt The prompt text to send to the model
    * @return The model's response as a plain string
    */
    public String prompt(String modelName, String userPrompt) {
    ChatModel model = buildModel(modelName); // Initialise the chosen model
    return model.chat(userPrompt); // Send prompt and return response
    }
   }