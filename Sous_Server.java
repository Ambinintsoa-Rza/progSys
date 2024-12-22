import java.io.*;
import java.net.*;
import java.util.Properties;

public class Sous_Server {
    private final int port;

    public Sous_Server(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Sub-server running on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new SubServerHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class SubServerHandler implements Runnable {
        private final Socket clientSocket;
        private final File storageDir = new File("subserver_files");

        public SubServerHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;

            // Assurez-vous que le répertoire de stockage existe
            if (!storageDir.exists()) {
                storageDir.mkdir();
            }
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                String command = in.readUTF(); // "store", "retrieve", "ls", or "remove"

                switch (command.toLowerCase()) {
                    case "store":
                        handleStore(in, out);
                        break;
                    case "retrieve":
                        handleRetrieve(in, out);
                        break;
                    case "ls":
                        handleListFiles(out);
                        break;
                    case "remove":
                        handleRemoveFile(in, out);
                        break;
                    default:
                        out.writeUTF("Invalid command");
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleStore(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();
            int size = in.readInt();
            byte[] data = new byte[size];
            in.readFully(data);

            File file = new File(storageDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }

            out.writeUTF("Stored successfully");
            System.out.println("File stored: " + fileName);
        }

        private void handleRetrieve(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();
            File file = new File(storageDir, fileName);

            if (file.exists()) {
                byte[] data = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(data);
                }

                out.writeInt(data.length);
                out.write(data);
                System.out.println("File sent: " + fileName);
            } else {
                out.writeInt(0); // File not found
                System.out.println("File not found: " + fileName);
            }
        }

        private void handleListFiles(DataOutputStream out) throws IOException {
            File[] files = storageDir.listFiles();
            if (files != null) {
                out.writeInt(files.length);
                for (File file : files) {
                    out.writeUTF(file.getName());
                }
            } else {
                out.writeInt(0); // No files
                System.out.println("J ai rien trouver");
            }
        }

        private void handleRemoveFile(DataInputStream in, DataOutputStream out) throws IOException {
            String fileName = in.readUTF();
            File file = new File(storageDir, fileName);

            if (file.exists() && file.delete()) {
                out.writeUTF("File " + fileName + " deleted successfully.");
                System.out.println("File deleted: " + fileName);
            } else {
                out.writeUTF("Failed to delete file " + fileName + ".");
                System.out.println("Failed to delete file: " + fileName);
            }
        }
    }

    public static void main(String[] args) {
        int port;

        // Charger le port depuis le fichier config.txt
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream("../Server/config/config.txt"));

            if (args.length == 0) {
                System.err.println("Please provide the sub-server identifier as an argument.");
                return;
            }

            String key = "SUB_SERVER_" + args[0] + "_PORT";
            if (!properties.containsKey(key)) {
                System.err.println("No port configuration found for key: " + key);
                return;
            }

            port = Integer.parseInt(properties.getProperty(key));
        } catch (IOException e) {
            System.err.println("Failed to load config file: " + e.getMessage());
            return;
        }

        new Sous_Server(port).start();
    }
}
