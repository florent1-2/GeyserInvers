import java.io.*;
import java.net.*;

public class ProxyBridge implements Runnable {
    private final Socket inSocket;
    private final Socket outSocket;

    public ProxyBridge(Socket inSocket, Socket outSocket) {
        this.inSocket = inSocket;
        this.outSocket = outSocket;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(inSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(outSocket.getOutputStream())) {

            while (true) {
                int packetLength;
                try {
                    packetLength = readVarInt(in);
                } catch (IOException e) {
                    break; // fin de stream
                }
                int packetId = readVarInt(in);

                System.out.println("Paquet Java revu ID=0x" + Integer.toHexString(packetId));

                // Lire le reste du paquet
                byte[] payload = new byte[packetLength - getVarIntSize(packetId)];
                in.readFully(payload);

                // TODO: Decoder ici un paquet specifique selon l'ID

                // Pour l instant, on transmet le paquet brut au serveur Bedrock
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                writeVarInt(dos, packetId);
                dos.write(payload);
                byte[] packetToSend = baos.toByteArray();

                writeVarInt(out, packetToSend.length);
                out.write(packetToSend);
                out.flush();
            }

        } catch (IOException e) {
            // Socket ferme ou erreur
        } finally {
            try { inSocket.close(); } catch (IOException ignored) {}
            try { outSocket.close(); } catch (IOException ignored) {}
            System.out.println("Session fermee entre "
                + inSocket.getInetAddress() + " ? "
                + outSocket.getInetAddress());
        }
    }

    // --- VarInt utils ---
    private int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0, result = 0;
        byte read;
        do {
            read = in.readByte();
            result |= (read & 0x7F) << (7 * numRead++);
            if (numRead > 5) throw new IOException("VarInt trop long");
        } while ((read & 0x80) != 0);
        return result;
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private int getVarIntSize(int value) {
        int size = 0;
        do {
            value >>>= 7;
            size++;
        } while (value != 0);
        return size;
    }
}
