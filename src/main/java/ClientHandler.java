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

                    // --- MUDANÇA PRINCIPAL AQUI ---
                    // Agora passamos a RESPOSTA para o método de verificação
                    if (isSuccessfulCloseRequest(jsonRequest, jsonResponse)) {
                        System.out.println("Cliente " + clientId + " solicitou encerramento (Logout/Delete) e obteve sucesso. Fechando conexão.");
                        break; // Encerra o loop e fecha o socket
                    }
                    // --- FIM DA MUDANÇA ---
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

    /**
     * MÉTODO ATUALIZADO:
     * Verifica se a requisição é de encerramento (Logout/Delete) E
     * se a resposta do servidor foi de SUCESSO (status 2xx).
     *
     * @param jsonRequest  A string da requisição original
     * @param jsonResponse O JSON da resposta que foi enviada
     * @return true se a conexão deve ser fechada, false caso contrário
     */
    private boolean isSuccessfulCloseRequest(String jsonRequest, JSONObject jsonResponse) {
        try {
            // 1. Verifica a RESPOSTA:
            // Se o status NÃO for de sucesso (ex: "403", "500"), não feche a conexão.
            String status = jsonResponse.optString("status", "500");
            if (!status.startsWith("2")) {
                return false; // Se foi um erro (como o 403 do admin), mantenha a conexão
            }

            // 2. Se a resposta FOI um sucesso, verifique a REQUISIÇÃO:
            // Apenas feche se foi uma operação de Logout ou Exclusão
            JSONObject req = new JSONObject(jsonRequest);
            String operacao = req.optString("operacao");
            return operacao.equals("LOGOUT") || operacao.equals("EXCLUIR_PROPRIO_USUARIO");

        } catch (Exception e) {
            return false;
        }
    }
}