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
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        // Cada handler tem seu próprio router, que por sua vez cria os serviços
        this.requestRouter = new RequestRouter();
    }

    @Override
    public void run() {
        // Usa try-with-resources para garantir que o socket e os streams sejam fechados [cite: 82]
        try (
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            this.out = out;
            this.in = in;
            String jsonRequest;

            // Loop de leitura: continua lendo enquanto o cliente enviar dados
            while ((jsonRequest = in.readLine()) != null) {
                System.out.println("[CLIENTE->SERVIDOR] Recebido: " + jsonRequest);

                // Processa a requisição
                JSONObject jsonResponse = requestRouter.handleRequest(jsonRequest);

                // Envia a resposta de volta ao cliente
                String responseString = jsonResponse.toString();
                System.out.println("[SERVIDOR->CLIENTE] Enviando: " + responseString);
                out.println(responseString);

                // Se a operação foi LOGOUT ou EXCLUIR_PROPRIO_USUARIO, o cliente espera
                // que a conexão seja encerrada (conforme NetworkService.java e requisitos [cite: 92])
                if (isCloseConnectionRequest(jsonRequest)) {
                    break; // Sai do loop, o try-with-resources fechará o socket
                }
            }
        } catch (IOException e) {
            System.err.println("Cliente desconectado (ou erro de I/O): " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
            System.out.println("Conexão com " + clientSocket.getInetAddress() + " encerrada.");
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