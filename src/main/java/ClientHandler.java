import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final RequestRouter requestRouter;
    private final ServerGui gui; // <-- NOVO
    private final String clientId; // <-- NOVO

    // ALTERAÇÃO: Construtor modificado
    public ClientHandler(Socket socket, ServerGui gui) {
        this.clientSocket = socket;
        this.gui = gui; // Armazena a referência da GUI
        this.clientId = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        this.requestRouter = new RequestRouter();
    }

    @Override
    public void run() {
        // A Lógica de I/O
        try {
            // NOVO: Informa a GUI que este cliente está ativo
            gui.addActiveClient(this.clientId);

            // O try-with-resources gerencia o fechamento dos streams
            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                String jsonRequest;

                while ((jsonRequest = in.readLine()) != null) {
                    // O System.out.println agora vai para a GUI
                    System.out.println("[" + clientId + " -> SVR] " + jsonRequest);

                    JSONObject jsonResponse = requestRouter.handleRequest(jsonRequest);

                    String responseString = jsonResponse.toString();
                    System.out.println("[SVR -> " + clientId + "] " + responseString);
                    out.println(responseString);

                    if (isCloseConnectionRequest(jsonRequest)) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            if (!clientSocket.isClosed()) {
                System.err.println("Erro de I/O com " + clientId + ": " + e.getMessage());
            }
        } finally {
            // NOVO: Informa a GUI que este cliente desconectou
            gui.removeActiveClient(this.clientId);

            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
            System.out.println("Conexão com " + clientId + " encerrada.");
        }
    }

    private boolean isCloseConnectionRequest(String jsonRequest) {
        try {
            JSONObject req = new JSONObject(jsonRequest);
            String operacao = req.optString("operacao");
            return operacao.equals("LOGOUT") || operacao.equals("EXCLUIR_PROPRIO_USUARIO");
        } catch (Exception e) {
            return false;
        }
    }
}