package iped.app.ui.ai.view;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Timer;
import iped.app.ui.ai.model.AIChatMessage;

/**
 * Classe especialista responsável por gerenciar a fila e a animação (efeito teletipo)
 * dos tokens de texto recebidos da LLM em segundo plano.
 */
public class ChatStreamAnimator {

    private static final int STREAM_APPEND_DELAY_MS = 30;
    private static final Pattern STREAM_PART_PATTERN = Pattern.compile("\\S+|\\s+");

    private final Timer streamTimer;
    private final List<String> streamQueue = new ArrayList<>();
    private final Runnable onTickUpdateAction;

    private AIChatMessage streamingMessage;
    private Runnable streamDrainAction;

    /**
     * @param onTickUpdateAction Callback executado a cada palavra adicionada para atualizar a UI.
     */
    public ChatStreamAnimator(Runnable onTickUpdateAction) {
        this.onTickUpdateAction = onTickUpdateAction;
        this.streamTimer = new Timer(STREAM_APPEND_DELAY_MS, e -> onTimerTick());
    }

    public void beginStreaming(AIChatMessage message) {
        this.streamingMessage = message;
        this.streamQueue.clear();
        this.streamDrainAction = null;
    }

    public void enqueueToken(String token) {
        if (streamingMessage == null || token == null || token.isEmpty()) {
            return;
        }

        Matcher matcher = STREAM_PART_PATTERN.matcher(token);
        while (matcher.find()) {
            streamQueue.add(matcher.group());
        }

        if (!streamQueue.isEmpty() && !streamTimer.isRunning()) {
            streamTimer.start();
        }
    }

    private void onTimerTick() {
        if (streamingMessage == null) {
            streamTimer.stop();
            return;
        }

        if (streamQueue.isEmpty()) {
            streamTimer.stop();
            runPendingDrainAction();
            return;
        }

        String part = streamQueue.remove(0);
        streamingMessage.appendContent(part);
        
        // Notifica o painel que o conteúdo mudou e a tela precisa ser atualizada
        onTickUpdateAction.run();
    }

    public void completeStreaming(Runnable onDrained) {
        if (streamQueue.isEmpty() && !streamTimer.isRunning()) {
            onDrained.run();
            resetState();
        } else {
            streamDrainAction = () -> {
                onDrained.run();
                resetState();
            };
        }
    }

    private void runPendingDrainAction() {
        if (streamDrainAction == null) {
            return;
        }
        Runnable action = streamDrainAction;
        streamDrainAction = null;
        action.run();
    }

    public void resetState() {
        if (streamTimer.isRunning()) {
            streamTimer.stop();
        }
        streamQueue.clear();
        streamDrainAction = null;
        streamingMessage = null;
    }
}