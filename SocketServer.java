import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class SocketServer {
    private static int PORT;
    private static List<String> subServerHosts = new ArrayList<>();
    private static List<Integer> subServerPorts = new ArrayList<>();

    public static void main(String[] args) {
        // Lire les informations depuis le fichier de configuration
        loadServerConfig("C:/Users/ASUS/Desktop/ls/Server/config/config.txt");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Main server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadServerConfig(String configFilePath) {
        Properties properties = new Properties();

        try (FileInputStream fileInput = new FileInputStream(configFilePath)) {
            properties.load(fileInput);

            // Charger le port du serveur principal
            PORT = Integer.parseInt(properties.getProperty("SERVER_PORT"));

            // Charger les IP et les ports des sous-serveurs
            subServerHosts.add(properties.getProperty("SUB_SERVER_1_HOST"));
            subServerHosts.add(properties.getProperty("SUB_SERVER_2_HOST"));
            subServerHosts.add(properties.getProperty("SUB_SERVER_3_HOST"));

            subServerPorts.add(Integer.parseInt(properties.getProperty("SUB_SERVER_1_PORT")));
            subServerPorts.add(Integer.parseInt(properties.getProperty("SUB_SERVER_2_PORT")));
            subServerPorts.add(Integer.parseInt(properties.getProperty("SUB_SERVER_3_PORT")));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                String command = in.readUTF(); // "upload", "download", "ls", or "remove"

                switch (command.toLowerCase()) {
                    case "upload":
                        handleUpload(in, out);
                        break;
                    case "download":
                        handleDownload(in, out);
                        break;
                    case "ls":
                        handleListFiles(out);
                        break;
                    case "remove":
                        handleRemoveFile(in, out);
                        break;
                    default:
                        out.writeUTF("Invalid command");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleUpload(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();
            long fileSize = in.readLong();
            byte[] fileData = new byte[(int) fileSize];
            in.readFully(fileData);

            // Diviser le fichier en trois parties
            int partSize = fileData.length / 3;
            byte[] part1 = Arrays.copyOfRange(fileData, 0, partSize);
            byte[] part2 = Arrays.copyOfRange(fileData, partSize, 2 * partSize);
            byte[] part3 = Arrays.copyOfRange(fileData, 2 * partSize, fileData.length);

            // Envoyer les parties aux sous-serveurs
            sendToSubServer(part1, 0, fileName + "_part1");
            sendToSubServer(part2, 1, fileName + "_part2");
            sendToSubServer(part3, 2, fileName + "_part3");

            out.writeUTF("Upload successful");
        }

        private void sendToSubServer(byte[] data, int subServerIndex, String fileName) throws IOException {
            try (Socket socket = new Socket(subServerHosts.get(subServerIndex), subServerPorts.get(subServerIndex));
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                out.writeUTF("store");
                out.writeUTF(fileName);
                out.writeInt(data.length);
                out.write(data);
            }
        }

        // Récupérer les fichiers depuis les sous-serveurs
        private void handleDownload(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();

            // Récupérer les parties depuis les sous-serveurs
            byte[] part1 = retrieveFromSubServer(0, fileName + "_part1");
            byte[] part2 = retrieveFromSubServer(1, fileName + "_part2");
            byte[] part3 = retrieveFromSubServer(2, fileName + "_part3");

            // Recomposer le fichier
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(part1);
            outputStream.write(part2);
            outputStream.write(part3);
            byte[] fileData = outputStream.toByteArray();

            // Envoyer le fichier complet au client
            out.writeLong(fileData.length);
            out.write(fileData);
        }

        private byte[] retrieveFromSubServer(int subServerIndex, String fileName) throws IOException {
            try (Socket socket = new Socket(subServerHosts.get(subServerIndex), subServerPorts.get(subServerIndex));
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                out.writeUTF("retrieve");
                out.writeUTF(fileName);

                int size = in.readInt();
                byte[] data = new byte[size];
                in.readFully(data);
                return data;
            }
        }

        // Liste des fichiers
        private void handleListFiles(DataOutputStream out) throws IOException {
            out.writeUTF("Files on server:");
            for (int i = 0; i < subServerPorts.size(); i++) {
                try (Socket socket = new Socket(subServerHosts.get(i), subServerPorts.get(i));
                     DataOutputStream subOut = new DataOutputStream(socket.getOutputStream());
                     DataInputStream subIn = new DataInputStream(socket.getInputStream())) {

                    subOut.writeUTF("ls");
                    int fileCount = subIn.readInt();
                    out.writeUTF("Sub-server " + (i + 1) + ":");
                    for (int j = 0; j < fileCount; j++) {
                        out.writeUTF(" - " + subIn.readUTF());
                    }
                }
            }
        }

        private void handleRemoveFile(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();
            boolean success = true;

            // Envoyer la commande de suppression aux sous-serveurs
            for (int i = 0; i < subServerPorts.size(); i++) {
                try (Socket socket = new Socket(subServerHosts.get(i), subServerPorts.get(i));
                     DataOutputStream subOut = new DataOutputStream(socket.getOutputStream());
                     DataInputStream subIn = new DataInputStream(socket.getInputStream())) {

                    subOut.writeUTF("remove");
                    subOut.writeUTF(fileName + "_part" + (i + 1));
                    String response = subIn.readUTF();

                    if (!response.contains("deleted successfully")) {
                        success = false;
                    }
                }
            }

            if (success) {
                out.writeUTF("File " + fileName + " removed successfully.");
            } else {
                out.writeUTF("Failed to remove file " + fileName + " from one or more sub-servers.");
            }
        }
    }
}


// C:/Users/ASUS/Desktop/ls/Server/config/config.txt

// C:/Users/ASUS/Desktop/ls/Client_Files/fde.jpeg