package net.moonvibe.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Minimaler Discord-RPC-Client. Spricht direkt mit der Discord-App über deren lokale
 * Schnittstelle (Named Pipe unter Windows, Unix-Socket sonst) – ganz ohne native Bibliotheken.
 */
final class DiscordIpc implements Closeable {
	private static final int OP_HANDSHAKE = 0;
	private static final int OP_FRAME = 1;

	private final Transport transport;

	private DiscordIpc(Transport transport) {
		this.transport = transport;
	}

	/** Verbindet sich mit Discord oder wirft eine IOException, wenn Discord nicht läuft. */
	static DiscordIpc connect(String clientId) throws IOException {
		IOException last = new IOException("Discord läuft nicht");
		for (int i = 0; i < 10; i++) {
			try {
				DiscordIpc ipc = new DiscordIpc(Transport.open(i));
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

	void setActivity(JsonObject activity) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
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
		transport.write(buf.array());
	}

	private JsonObject receive() throws IOException {
		ByteBuffer header = ByteBuffer.wrap(transport.read(8)).order(ByteOrder.LITTLE_ENDIAN);
		header.getInt(); // opcode
		int length = header.getInt();
		String json = new String(transport.read(length), StandardCharsets.UTF_8);
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Override
	public void close() {
		try {
			transport.close();
		} catch (IOException ignored) {
		}
	}

	private interface Transport extends Closeable {
		void write(byte[] data) throws IOException;

		byte[] read(int length) throws IOException;

		static Transport open(int index) throws IOException {
			if (System.getProperty("os.name").toLowerCase().contains("win")) {
				RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + index, "rw");
				return new Transport() {
					public void write(byte[] data) throws IOException {
						pipe.write(data);
					}

					public byte[] read(int length) throws IOException {
						byte[] out = new byte[length];
						pipe.readFully(out);
						return out;
					}

					public void close() throws IOException {
						pipe.close();
					}
				};
			}

			Path socket = null;
			for (String env : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
				String dir = System.getenv(env);
				if (dir != null && Files.exists(Path.of(dir, "discord-ipc-" + index))) {
					socket = Path.of(dir, "discord-ipc-" + index);
					break;
				}
			}
			if (socket == null) socket = Path.of("/tmp", "discord-ipc-" + index);
			SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
			return new Transport() {
				public void write(byte[] data) throws IOException {
					ByteBuffer buf = ByteBuffer.wrap(data);
					while (buf.hasRemaining()) channel.write(buf);
				}

				public byte[] read(int length) throws IOException {
					ByteBuffer buf = ByteBuffer.allocate(length);
					while (buf.hasRemaining()) {
						if (channel.read(buf) < 0) throw new IOException("Verbindung zu Discord getrennt");
					}
					return buf.array();
				}

				public void close() throws IOException {
					channel.close();
				}
			};
		}
	}
}
