import java.io.*;
import java.net.*;
import java.util.Properties;

public class SocketClient {
    private static String SERVER_HOST;
    private static int SERVER_PORT;

    public static void main(String[] args) {
        // Charger la configuration depuis le fichier config.txt
        loadServerConfig("C:/Users/ASUS/Desktop/ls/Server/config/config.txt");

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.println("\nEnter command (upload/download/ls/remove/exit): ");
                String command = console.readLine();
                out.writeUTF(command);

                if ("upload".equalsIgnoreCase(command)) {
                    handleUpload(console, in, out);
                } else if ("download".equalsIgnoreCase(command)) {
                    handleDownload(console, in, out);
                } else if ("ls".equalsIgnoreCase(command)) {
                    handleListFiles(in);
                } else if ("remove".equalsIgnoreCase(command)) {
                    handleRemoveFile(console, in, out);
                } else if ("exit".equalsIgnoreCase(command)) {
                    System.out.println("Exiting...");
                    break;
                } else {
                    System.out.println("Invalid command.");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadServerConfig(String configFilePath) {
        Properties properties = new Properties();
        try (FileInputStream fileInput = new FileInputStream(configFilePath)) {
            properties.load(fileInput);

            // Charger les configurations du serveur
            SERVER_HOST = properties.getProperty("SERVER_HOST");
            SERVER_PORT = Integer.parseInt(properties.getProperty("SERVER_PORT"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleUpload(BufferedReader console, DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Enter file path: ");
        String filePath = console.readLine();
        File file = new File(filePath);

        if (file.exists()) {
            out.writeUTF(file.getName());
            out.writeLong(file.length());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[(int) file.length()];
                fis.read(buffer);
                out.write(buffer);
            }

            System.out.println(in.readUTF());
        } else {
            System.out.println("File does not exist.");
        }
    }

    private static void handleDownload(BufferedReader console, DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Enter file name: ");
        String fileName = console.readLine();
        out.writeUTF(fileName);

        long fileSize = in.readLong();
        if (fileSize > 0) {
            byte[] fileData = new byte[(int) fileSize];
            in.readFully(fileData);

            System.out.println("Enter destination path (include file name): ");
            String destPath = console.readLine();

            try (FileOutputStream fos = new FileOutputStream(destPath)) {
                fos.write(fileData);
            }

            System.out.println("File downloaded successfully.");
        } else {
            System.out.println("File not found on server.");
        }
    }

    private static void handleListFiles(DataInputStream in) throws IOException {
        System.out.println("Files on server:");

        while (true) {
            try {
                String file = in.readUTF(); // Lire le nom du fichier
                if (file.equals("No more files.")) {
                    break; // Quitter la boucle si le signal de fin est reçu
                }
                System.out.println(file); // Afficher le nom du fichier
            } catch (EOFException e) {
                System.err.println("End of stream reached unexpectedly.");
                break; // Arrêter la boucle en cas de fin de flux
            }
        }
    }

    private static void handleRemoveFile(BufferedReader console, DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Enter file name to delete: ");
        String fileName = console.readLine();
        out.writeUTF(fileName);

        String response = in.readUTF();
        System.out.println(response);
    }
}
