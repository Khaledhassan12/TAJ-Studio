package pro.sketchware.ai.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable chat request handed to an engine: conversation messages, optional
 * system prompt, optional tool specs and sampling parameters.
 */
public final class AIRequest {

    public final String systemPrompt;
    public final List<AIMessage> messages;
    public final List<ToolSpec> tools;
    public final String model;
    public final float temperature;
    public final int maxTokens;

    private AIRequest(Builder b) {
        systemPrompt = b.systemPrompt;
        messages = Collections.unmodifiableList(new ArrayList<>(b.messages));
        tools = Collections.unmodifiableList(new ArrayList<>(b.tools));
        model = b.model;
        temperature = b.temperature;
        maxTokens = b.maxTokens;
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }

    public static final class Builder {
        private String systemPrompt = null;
        private final List<AIMessage> messages = new ArrayList<>();
        private final List<ToolSpec> tools = new ArrayList<>();
        private String model = "";
        private float temperature = 0.7f;
        private int maxTokens = 4096;

        public Builder systemPrompt(String prompt) {
            this.systemPrompt = prompt;
            return this;
        }

        public Builder messages(List<AIMessage> conversation) {
            messages.clear();
            if (conversation != null) {
                messages.addAll(conversation);
            }
            return this;
        }

        public Builder addMessage(AIMessage message) {
            if (message != null) {
                messages.add(message);
            }
            return this;
        }

        public Builder tools(List<ToolSpec> toolSpecs) {
            tools.clear();
            if (toolSpecs != null) {
                tools.addAll(toolSpecs);
            }
            return this;
        }

        public Builder model(String model) {
            this.model = model == null ? "" : model;
            return this;
        }

        public Builder temperature(float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public AIRequest build() {
            return new AIRequest(this);
        }
    }
}
