import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Uma classe utilitária que redireciona um OutputStream (como System.out)
 * para uma JTextArea, garantindo que a atualização da UI seja thread-safe.
 */
public class TextAreaOutputStream extends OutputStream {

    private final JTextArea textArea;
    private final StringBuilder sb = new StringBuilder();

    public TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void flush() {
        // Envia o que estiver no buffer para a JTextArea
        final String text = sb.toString();
        sb.setLength(0); // Limpa o buffer

        SwingUtilities.invokeLater(() -> {
            textArea.append(text);
            // Auto-scroll para o final
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    @Override
    public void write(int b) throws IOException {
        if (b == '\r') {
            // Ignora carriage return
            return;
        }

        if (b == '\n') {
            // Se for newline, é hora de "flushar" o buffer para a UI
            sb.append((char) b);
            flush();
        } else {
            // Adiciona o caractere ao buffer
            sb.append((char) b);
        }
    }
}