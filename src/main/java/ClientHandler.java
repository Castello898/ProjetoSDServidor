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
    private final ServerGui gui;
    private final String clientId;

    // NOVO: Guarda o ID do usuário logado neste socket
    private Integer loggedUserId = null;

    public ClientHandler(Socket socket, ServerGui gui) {
        this.clientSocket = socket;
        this.gui = gui;
        this.clientId = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        // Passa a GUI para o Router (que passará para o Controller)
        this.requestRouter = new RequestRouter(gui);
    }

    // NOVO: Método chamado pela GUI para derrubar conexão
    public void forceClose() {
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            gui.addActiveClient(this.clientId);

            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                String jsonRequest;

                while ((jsonRequest = in.readLine()) != null) {
                    System.out.println("[" + clientId + " -> SVR] " + jsonRequest);

                    JSONObject jsonResponse = requestRouter.handleRequest(jsonRequest);

                    String responseString = jsonResponse.toString();
                    System.out.println("[SVR -> " + clientId + "] " + responseString);
                    out.println(responseString);

                    // --- NOVO: Lógica de Registro de Sessão ---
                    if (loggedUserId == null && isLoginSuccess(jsonRequest, jsonResponse)) {
                        try {
                            String token = jsonResponse.getString("token");
                            // Usa JwtService apenas para extrair o ID
                            int id = new JwtService().validateAndGetClaims(token).get("id", Integer.class);
                            this.loggedUserId = id;
                            gui.registerUser(id, this); // Registra na GUI
                        } catch (Exception e) {
                            System.err.println("Erro ao registrar sessão: " + e.getMessage());
                        }
                    }

                    if (isSuccessfulCloseRequest(jsonRequest, jsonResponse)) {
                        System.out.println("Cliente " + clientId + " saiu voluntariamente.");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            if (!clientSocket.isClosed()) {
                System.err.println("Erro de I/O (ou Kick) com " + clientId + ": " + e.getMessage());
            }
        } finally {
            // NOVO: Remove do mapa de usuários online
            if (loggedUserId != null) {
                gui.unregisterUser(loggedUserId);
            }

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

    private boolean isLoginSuccess(String jsonRequest, JSONObject jsonResponse) {
        try {
            JSONObject req = new JSONObject(jsonRequest);
            String op = req.optString("operacao");
            String status = jsonResponse.optString("status");
            return "LOGIN".equals(op) && "200".equals(status);
        } catch (Exception e) { return false; }
    }

    private boolean isSuccessfulCloseRequest(String jsonRequest, JSONObject jsonResponse) {
        try {
            String status = jsonResponse.optString("status", "500");
            if (!status.startsWith("2")) return false;

            JSONObject req = new JSONObject(jsonRequest);
            String operacao = req.optString("operacao");
            return operacao.equals("LOGOUT") || operacao.equals("EXCLUIR_PROPRIO_USUARIO");

        } catch (Exception e) {
            return false;
        }
    }
}