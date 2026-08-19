package pro.sketchware.ai.config;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.core.Protocol;
import pro.sketchware.ai.core.ProviderProfile;

/**
 * Static catalog of every built-in provider profile, mapped to the exact
 * official contracts. Three protocol families cover everything: native
 * Anthropic, native Gemini and OpenAI-compatible (the universal escape hatch
 * for LocalAI, LM Studio, llama.cpp, vLLM, LiteLLM and any compatible server).
 *
 * The base URL always INCLUDES any version path (e.g. ".../v1"). Engines append
 * ONLY endpoint paths ("chat/completions", "messages", "models"...) and never
 * auto-append "/v1", so no double-version bug can appear.
 */
public final class ProviderCatalog {

    private ProviderCatalog() {
    }

    public static List<ProviderProfile> builtInProfiles() {
        List<ProviderProfile> list = new ArrayList<>();

        list.add(openAi("openai", "OpenAI", "https://api.openai.com/v1",
                "gpt-4o", "gpt-4o-mini", "o3-mini"));
        list.add(new ProviderProfile.Builder("anthropic", "Anthropic", Protocol.ANTHROPIC)
                .baseUrl("https://api.anthropic.com")
                .models("claude-sonnet-4-5", "claude-opus-4-1", "claude-3-5-haiku-latest")
                .build());
        list.add(new ProviderProfile.Builder("gemini", "Google Gemini", Protocol.GEMINI)
                .baseUrl("https://generativelanguage.googleapis.com")
                .models("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash")
                .build());
        list.add(new ProviderProfile.Builder("google-studio-ai", "Google AI Studio", Protocol.GEMINI)
                .baseUrl("https://generativelanguage.googleapis.com")
                .models("gemini-2.5-flash", "gemini-2.5-flash-lite")
                .build());
        list.add(new ProviderProfile.Builder("google", "Google Vertex (compatible)", Protocol.GEMINI)
                .baseUrl("https://generativelanguage.googleapis.com")
                .models("gemini-2.5-pro")
                .build());
        list.add(openAi("grok", "xAI Grok", "https://api.x.ai/v1",
                "grok-4", "grok-3", "grok-3-mini"));
        list.add(openAi("groq", "Groq", "https://api.groq.com/openai/v1",
                "llama-3.3-70b-versatile", "llama-3.1-8b-instant"));
        list.add(openAi("qwen", "Qwen (DashScope)", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen-max", "qwen-plus", "qwen-turbo"));
        list.add(openAi("deepseek", "DeepSeek", "https://api.deepseek.com",
                "deepseek-chat", "deepseek-reasoner"));
        list.add(openAi("meta", "Meta (compatible gateway)", "https://api.llama.com/compat/v1",
                "llama-4-maverick", "llama-4-scout"));
        list.add(openAi("copilot", "GitHub Models", "https://models.inference.ai.azure.com",
                "gpt-4o", "gpt-4o-mini"));
        list.add(openAi("perplexity", "Perplexity", "https://api.perplexity.ai",
                "sonar", "sonar-pro"));
        list.add(new ProviderProfile.Builder("openrouter", "OpenRouter", Protocol.OPENAI_COMPATIBLE)
                .baseUrl("https://openrouter.ai/api/v1")
                .header("HTTP-Referer", "https://taj.studio")
                .header("X-Title", "TAJ Studio")
                .models("anthropic/claude-sonnet-4.5", "openai/gpt-4o", "google/gemini-2.5-pro",
                        "meta-llama/llama-3.3-70b-instruct")
                .build());
        list.add(openAi("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1",
                "meta/llama-3.1-70b-instruct"));
        list.add(new ProviderProfile.Builder("ollama", "Ollama (local)", Protocol.OPENAI_COMPATIBLE)
                .baseUrl("http://localhost:11434/v1")
                .requiresKey(false)
                .skipAuth(true)
                .models("llama3.2", "qwen2.5-coder", "mistral")
                .build());
        list.add(openAi("zai", "Z.ai (GLM)", "https://api.z.ai/api/paas/v4",
                "glm-4.6", "glm-4-plus"));
        list.add(openAi("huggingface", "Hugging Face Router", "https://router.huggingface.co/v1",
                "deepseek-ai/DeepSeek-V3-0324"));
        list.add(openAi("mistral", "Mistral", "https://api.mistral.ai/v1",
                "mistral-large-latest", "ministral-8b-latest"));
        list.add(openAi("alibaba", "Alibaba Cloud (DashScope)", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen-max", "qwen-plus"));
        list.add(openAi("ai21", "AI21", "https://api.ai21.com/studio/v1",
                "jamba-large", "jamba-mini"));
        list.add(new ProviderProfile.Builder("azure", "Azure AI Foundry", Protocol.OPENAI_COMPATIBLE)
                .baseUrl("https://YOUR-RESOURCE.openai.azure.com/openai/deployments/YOUR-DEPLOYMENT")
                .apiKeyHeaderAuth(true)
                .urlQuerySuffix("api-version=2024-10-21")
                .models("gpt-4o", "gpt-4o-mini")
                .build());
        list.add(openAi("together", "Together AI", "https://api.together.xyz/v1",
                "meta-llama/Llama-3.3-70B-Instruct-Turbo"));
        list.add(openAi("cerebras", "Cerebras", "https://api.cerebras.ai/v1",
                "llama-3.3-70b"));
        list.add(openAi("agentrouter", "AgentRouter (compatible)", "https://api.agentrouter.com/v1",
                "gpt-4o-mini"));

        // Universal escape hatch: any OpenAI-compatible URL the user enters.
        list.add(new ProviderProfile.Builder(ProviderProfile.CUSTOM_ID, "Custom (OpenAI-compatible)", Protocol.OPENAI_COMPATIBLE)
                .baseUrl("")
                .requiresKey(false)
                .custom(true)
                .build());

        return list;
    }

    private static ProviderProfile openAi(String id, String displayName, String baseUrl, String... models) {
        return new ProviderProfile.Builder(id, displayName, Protocol.OPENAI_COMPATIBLE)
                .baseUrl(baseUrl)
                .models(models)
                .build();
    }
}
