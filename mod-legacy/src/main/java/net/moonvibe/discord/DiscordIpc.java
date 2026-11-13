package net.moonvibe.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Minimaler Discord-RPC-Client. Spricht direkt mit der Discord-App über deren lokale
 * Schnittstelle (Named Pipe unter Windows) – ganz ohne native Bibliotheken.
 */
final class DiscordIpc implements Closeable {
	private static final int OP_HANDSHAKE = 0;
	private static final int OP_FRAME = 1;

	private final RandomAccessFile pipe;

	private DiscordIpc(RandomAccessFile pipe) {
		this.pipe = pipe;
	}

	/** Verbindet sich mit Discord oder wirft eine IOException, wenn Discord nicht läuft. */
	static DiscordIpc connect(String clientId) throws IOException {
		if (!System.getProperty("os.name").toLowerCase().contains("win")) throw new IOException("nur unter Windows");
		IOException last = new IOException("Discord läuft nicht");
		for (int i = 0; i < 10; i++) {
			try {
				DiscordIpc ipc = new DiscordIpc(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
				JsonObject hello = new JsonObject();
				hello.addProperty("v", 1);
				hello.addProperty("client_id", clientId);
				ipc.send(OP_HANDSHAKE, hello);
				ipc.receive(); // READY
				return ipc;
			} catch (IOException e) {
				last = e;
			}
		}
		throw last;
	}

	private static long pid() {
		try {
			return Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
		} catch (Exception e) {
			return 0;
		}
	}

	void setActivity(JsonObject activity) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", pid());
		args.add("activity", activity);
		JsonObject payload = new JsonObject();
		payload.addProperty("cmd", "SET_ACTIVITY");
		payload.add("args", args);
		payload.addProperty("nonce", UUID.randomUUID().toString());
		send(OP_FRAME, payload);
		receive();
	}

	private void send(int op, JsonObject payload) throws IOException {
		byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(op).putInt(json.length).put(json);
		pipe.write(buf.array());
	}

	private JsonObject receive() throws IOException {
		byte[] head = new byte[8];
		pipe.readFully(head);
		ByteBuffer header = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
		header.getInt(); // opcode
		byte[] body = new byte[header.getInt()];
		pipe.readFully(body);
		return new JsonParser().parse(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Override
	public void close() {
		try {
			pipe.close();
		} catch (IOException ignored) {
		}
	}
}
