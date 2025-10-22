import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ServerMain {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite a porta para o servidor (ex: 12345): ");
        int port = Integer.parseInt(scanner.nextLine());

        // 1. Inicializa o banco de dados e cria a tabela/usuário admin se não existirem
        try {
            DatabaseService.getInstance().initializeDatabase();
            System.out.println("Banco de dados H2 inicializado com sucesso.");
        } catch (Exception e) {
            System.err.println("Falha fatal ao inicializar o banco de dados: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // 2. Cria um pool de threads para lidar com múltiplos clientes
        ExecutorService pool = Executors.newCachedThreadPool();

        // 3. Inicia o ServerSocket
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor VoteFlix® aguardando conexões na porta " + port + "...");

            while (true) {
                // 4. Aceita uma nova conexão de cliente
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress());

                // 5. Entrega o socket do cliente para um ClientHandler em uma nova thread
                pool.execute(new ClientHandler(clientSocket));
            }

        } catch (IOException e) {
            System.err.println("Erro no ServerSocket: " + e.getMessage());
            e.printStackTrace();
        }
    }
}