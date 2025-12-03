import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerGui extends JFrame {

    // Componentes da UI
    private JTextField portField;
    private JButton startButton;
    private JTextField ipField;
    private JTextArea logArea;
    private JList<String> activeClientsList;
    private DefaultListModel<String> activeClientsModel;
    private JTable dbTable;
    private JButton refreshDbButton;

    // Lógica do Servidor
    private ExecutorService pool;
    private ServerSocket serverSocket;
    private Thread serverThread;

    // --- NOVO: Mapa para rastrear UserID -> ClientHandler ---
    private final Map<Integer, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    public ServerGui() {
        setTitle("VoteFlix® Server - Control Panel");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // --- 1. Painel Superior (Controle) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("IP:"));
        ipField = new JTextField("N/A", 12);
        ipField.setEditable(false);
        topPanel.add(ipField);

        topPanel.add(new JLabel("Port:"));
        portField = new JTextField("12345", 5);
        topPanel.add(portField);

        startButton = new JButton("Start Server");
        startButton.addActionListener(e -> toggleServer());
        topPanel.add(startButton);

        add(topPanel, BorderLayout.NORTH);

        // --- 2. Painel Central (Logs e Listas) ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.7); // Log area fica com 70%

        // --- 2a. Painel de Logs (Direita) ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        mainSplit.setRightComponent(logScrollPane);

        // Redireciona System.out e System.err para o logArea
        TextAreaOutputStream taos = new TextAreaOutputStream(logArea);
        System.setOut(new PrintStream(taos, true));
        System.setErr(new PrintStream(taos, true));

        // --- 2b. Painel de Informações (Esquerda) ---
        JSplitPane infoSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        infoSplit.setResizeWeight(0.4); // 40% para lista de clientes

        // Lista de Clientes Ativos
        activeClientsModel = new DefaultListModel<>();
        activeClientsList = new JList<>(activeClientsModel);
        JScrollPane clientScrollPane = new JScrollPane(activeClientsList);
        clientScrollPane.setBorder(BorderFactory.createTitledBorder("Active Connections"));
        infoSplit.setTopComponent(clientScrollPane);

        // Visão do Banco de Dados
        JPanel dbPanel = new JPanel(new BorderLayout());
        dbTable = new JTable();
        JScrollPane dbScrollPane = new JScrollPane(dbTable);
        dbScrollPane.setBorder(BorderFactory.createTitledBorder("Database (Users Table)"));
        dbPanel.add(dbScrollPane, BorderLayout.CENTER);

        refreshDbButton = new JButton("Refresh DB View");
        refreshDbButton.addActionListener(e -> refreshDatabaseView());
        dbPanel.add(refreshDbButton, BorderLayout.SOUTH);
        infoSplit.setBottomComponent(dbPanel);

        mainSplit.setLeftComponent(infoSplit);
        add(mainSplit, BorderLayout.CENTER);

        // Inicialização
        pool = Executors.newCachedThreadPool();
        try {
            ipField.setText(InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            ipField.setText("Error getting IP");
        }
    }

    private void toggleServer() {
        if (serverThread == null || !serverThread.isAlive()) {
            startServer();
        } else {
            stopServer();
        }
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(portField.getText());
            portField.setEditable(false);
            startButton.setText("Stop Server");

            // Inicializa o DB ANTES de começar a aceitar conexões
            DatabaseService.getInstance().initializeDatabase();
            System.out.println("Banco de dados H2 inicializado com sucesso.");
            refreshDatabaseView(); // Carrega a tabela na UI

            // A lógica do servidor DEVE rodar em uma thread separada
            serverThread = new Thread(() -> {
                try (ServerSocket ss = new ServerSocket(port)) {
                    serverSocket = ss; // Armazena a referência para poder fechar
                    System.out.println("Servidor VoteFlix® aguardando conexões na porta " + port + "...");

                    while (!serverSocket.isClosed()) {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress());

                        // Passa a referência da GUI para o ClientHandler
                        pool.execute(new ClientHandler(clientSocket, this));
                    }
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("Erro no ServerSocket: " + e.getMessage());
                    }
                }
            });
            serverThread.start();

        } catch (NumberFormatException e) {
            System.err.println("Porta inválida.");
        } catch (Exception e) {
            System.err.println("Falha ao inicializar o banco de dados: " + e.getMessage());
        }
    }

    private void stopServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Isso vai interromper o loop .accept()
            }
            if (serverThread != null) {
                serverThread.join(1000); // Espera a thread do servidor morrer
            }
            pool.shutdownNow(); // Força o desligamento de todos os handlers
            pool = Executors.newCachedThreadPool(); // Cria um novo pool para o próximo start

            onlineUsers.clear(); // Limpa mapa de usuários

            System.out.println("Servidor parado.");
            portField.setEditable(true);
            startButton.setText("Start Server");
            activeClientsModel.clear();

        } catch (Exception e) {
            System.err.println("Erro ao parar o servidor: " + e.getMessage());
        }
    }

    private void refreshDatabaseView() {
        try {
            String[] columnNames = {"ID", "Username", "Role"};
            Object[][] data = DatabaseService.getInstance().getAllUsersForTable();
            dbTable.setModel(new DefaultTableModel(data, columnNames));
        } catch (SQLException e) {
            System.err.println("Falha ao atualizar a visão do DB: " + e.getMessage());
        }
    }

    // Métodos Thread-Safe para atualizar a UI
    public void addActiveClient(String clientId) {
        SwingUtilities.invokeLater(() -> activeClientsModel.addElement(clientId));
    }

    public void removeActiveClient(String clientId) {
        SwingUtilities.invokeLater(() -> activeClientsModel.removeElement(clientId));
    }

    // --- NOVOS MÉTODOS DE CONTROLE DE SESSÃO ---

    public void registerUser(int userId, ClientHandler client) {
        onlineUsers.put(userId, client);
        System.out.println("[SESSION] User ID " + userId + " logado e registrado.");
    }

    public void unregisterUser(int userId) {
        onlineUsers.remove(userId);
    }

    public void disconnectUser(int userId) {
        ClientHandler client = onlineUsers.remove(userId);
        if (client != null) {
            System.out.println("[ADMIN] Forçando desconexão do User ID " + userId);
            client.forceClose(); // Derruba o socket
        }
    }

    // Ponto de entrada
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerGui gui = new ServerGui();
            gui.setLocationRelativeTo(null); // Centraliza
            gui.setVisible(true);
        });
    }
}