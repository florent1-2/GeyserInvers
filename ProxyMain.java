import java.io.*;
import java.net.*;
import java.util.*;

public class ProxyMain {
    public static void main(String[] args) {
        try {
            Map<String, String> config = loadConfig("config.yml");
            String listenIp = config.getOrDefault("java_listen_ip", "0.0.0.0");
            int listenPort = Integer.parseInt(config.getOrDefault("java_listen_port", "25565"));

            ServerSocket serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(listenIp, listenPort));
            System.out.println("Proxy pret sur " + listenIp + ":" + listenPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connecte : " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Lecture handshake
            int packetLength = readVarInt(in);
            int packetId     = readVarInt(in);
            if (packetId != 0x00) {
                System.out.println("Paquet inattendu (handshake) ID=" + packetId);
                return;
            }
            // On lit le handshake (on peut stocker protocolVersion, address, port)
            readVarInt(in);           // protocolVersion
            String host = readString(in);
            in.readUnsignedShort();   // port
            int nextState = readVarInt(in);

            if (nextState == 1) {
                // STATUS (ping MOTD) -> on repond et ferme
                handleStatus(in, out);
                return;
            } else if (nextState == 2) {
                // LOGIN
                int length  = readVarInt(in);
                int loginId = readVarInt(in);
                if (loginId != 0x00) {
                    System.out.println("Paquet LOGIN inattendu ID=" + loginId);
                    return;
                }
                String playerName = readString(in);
                System.out.println("Debut LOGIN pour joueur : " + playerName);

                // Connexion vers Bedrock
                Map<String, String> cfg = loadConfig("config.yml");
                String bedIp   = cfg.getOrDefault("bedrock_target_ip", "127.0.0.1");
                int    bedPort = Integer.parseInt(cfg.getOrDefault("bedrock_target_port", "19132"));
                Socket bedrockSocket = new Socket(bedIp, bedPort);
                System.out.println("? Connecte a Bedrock " + bedIp + ":" + bedPort);

                // On lance le tunnel bidirectionnel avec fermeture automatique
                new Thread(new ProxyBridge(socket, bedrockSocket)).start();
                new Thread(new ProxyBridge(bedrockSocket, socket)).start();
            }

        } catch (IOException e) {
            System.err.println("Erreur session client : " + e.getMessage());
        }
    }

    private static void handleStatus(DataInputStream in, DataOutputStream out) throws IOException {
        // on lit la requete status
        readVarInt(in); // length
        readVarInt(in); // packetId (0x00)
        // on renvoie un MOTD statique
        String motd = "{\"version\":{\"name\":\"Proxy\",\"protocol\":1},"
                    + "\"players\":{\"max\":0,\"online\":0},"
                    + "\"description\":{\"text\":\"Java?Bedrock Proxy\"}}";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream resp = new DataOutputStream(buf);
        writeString(resp, motd);
        sendPacket(out, 0x00, buf.toByteArray());
        // lecture du ping
        readVarInt(in);
        readVarInt(in);
        long ping = in.readLong();
        ByteArrayOutputStream pong = new ByteArrayOutputStream();
        DataOutputStream pout = new DataOutputStream(pong);
        pout.writeLong(ping);
        sendPacket(out, 0x01, pong.toByteArray());
        System.out.println("Status traite, fermeture.");
    }

    // --- Lecture/ecriture VarInt & String & Packet ---
    private static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0, result = 0;
        byte read;
        do {
            read = in.readByte();
            result |= (read & 0x7F) << (7 * numRead++);
            if (numRead > 5) throw new RuntimeException("VarInt trop long");
        } while ((read & 0x80) != 0);
        return result;
    }
    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
    private static String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, "UTF-8");
    }
    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        writeVarInt(out, b.length);
        out.write(b);
    }
    private static void sendPacket(DataOutputStream out, int packetId, byte[] data) throws IOException {
        ByteArrayOutputStream pkt = new ByteArrayOutputStream();
        DataOutputStream p = new DataOutputStream(pkt);
        writeVarInt(p, packetId);
        p.write(data);
        byte[] raw = pkt.toByteArray();
        writeVarInt(out, raw.length);
        out.write(raw);
        out.flush();
    }

    private static Map<String, String> loadConfig(String path) {
        Map<String, String> cfg = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            r.lines().forEach(line -> {
                if (line.contains(":")) {
                    String[] p = line.split(":", 2);
                    cfg.put(p[0].trim(), p[1].trim().replace("\"", ""));
                }
            });
        } catch (IOException e) {
            System.err.println("Impossible de lire config.yml: " + e.getMessage());
        }
        return cfg;
    }
}
