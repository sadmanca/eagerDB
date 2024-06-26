package shared.messages;

import java.io.ObjectInputFilter.Status;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.log4j.*;
import shared.ConsoleColors;

public class BasicKVMessage implements KVMessage {
  private static final int STATUS_SIZE = 25;

  private static Logger logger = Logger.getRootLogger();
  private static final char LINE_FEED = 0x0A;
  private static final char RETURN = 0x0D;
  private static final String SECRET = "AzgLJSkqMm";

  private StatusType status;
  private String key;
  private String value;
  private Boolean localProtocol = true;

  String msg;
  byte[] msgBytes, externalMsgBytes;

  public BasicKVMessage(StatusType status, String key, String value) {
    this.status = status;
    this.key = key;
    this.value = value;

    this.msg = this.status.toString();
    if (this.key != null)
      this.msg += " " + this.key;
    if (this.value != null)
      this.msg += " " + this.value;
    this.msg.trim();

    this.msgBytes = toByteArray(status, key, value);
    this.externalMsgBytes = toByteArray(msg);
  }

  public BasicKVMessage(byte[] bytes) {
    this.parseBytes(addCtrChars(bytes));
    if (this.localProtocol) {
      this.msgBytes = addCtrChars(bytes);
    } else {
      this.externalMsgBytes = addCtrChars(bytes);
    }
  }

  public byte[] getMsgBytes() {
    if(this.localProtocol){
      return this.msgBytes;
    } else {
      return this.externalMsgBytes;
    }
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }
  
  public Boolean getLocalProtocol() {
    return localProtocol;
  }

  public void setLocalProtocl(Boolean val) {
    localProtocol = val;
  }

  public void changeKey(String key) {
      this.key = key;
  }
  
  public void changeValue(String value) {
    this.value = value;
  }

  public StatusType getStatus() {
    return status;
  }

  private void parseLocalProtocol(byte[] bytes) {
    this.logger.info(ConsoleColors.BLACK_BACKGROUND + "Internal Message" + ConsoleColors.RESET);
    this.localProtocol = true;
    try {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      buffer.position(buffer.position() + SECRET.length());
      // Extract StatusType (STATUS_SIZE bytes)
      byte[] statusBytes = new byte[STATUS_SIZE];
      buffer.get(statusBytes);
      String statusStr = new String(statusBytes).trim();
      this.status = StatusType.valueOf(statusStr);

      // Extract key length and key
      int keyLength = buffer.getInt();
      if (keyLength > 0) {
        byte[] keyBytes = new byte[keyLength];
        buffer.get(keyBytes);
        this.key = new String(keyBytes);
      }
      int valueLength = buffer.getInt();
      value = null;
      if (valueLength > 0) {
        byte[] valueBytes = new byte[valueLength];
        buffer.get(valueBytes);
        value = new String(valueBytes);
      }

    } catch (Exception e) {
      // Handle the error as needed
      this.status = StatusType.FAILED;
      this.key = "Message format is unknown";
      this.value = null;
    }
  }
  
  private void parseExternalProtocol(byte bytes[]) {
    this.logger.info(ConsoleColors.BLACK_BACKGROUND + "External Message" + ConsoleColors.RESET);
    this.localProtocol = false;
    String receivedMsg = new String(bytes).trim(); 
    String[] components = receivedMsg.split("\\s+");

    if (components.length < 1) {
      throw new IllegalArgumentException("Invalid message format: " + receivedMsg);
    }

    if (components.length > 0) {
      try {
        this.status = StatusType.valueOf(components[0].toUpperCase());
      } catch (IllegalArgumentException e) {
        return;
      }
    }

    if (components.length > 1) {
      this.key = components[1];
    }

    if (components.length > 2) {
      StringBuilder rawValue = new StringBuilder();

      for (int i = 2; i < components.length; i++) {
        rawValue.append(components[i]);
        if (i != components.length - 1) {
          rawValue.append(" ");
        }
      }
      this.value = rawValue.toString();
    }

    this.logger.info(ConsoleColors.BLACK_BACKGROUND + "Status: " + status + ConsoleColors.RESET);
    this.logger.info(ConsoleColors.BLACK_BACKGROUND + "Key: " + key + ConsoleColors.RESET);
    this.logger.info(ConsoleColors.BLACK_BACKGROUND + "Value: " + value + ConsoleColors.RESET);
  }

  private void parseBytes(byte[] bytes) {
    this.logger.info(ConsoleColors.BLACK_BACKGROUND + "Messaged received, now parsing" + ConsoleColors.RESET);
    int secretLength = SECRET.length();
    byte[] checkSecret = new byte[secretLength];

    if(bytes.length < secretLength) this.parseExternalProtocol(bytes);
    else {
      System.arraycopy(bytes, 0, checkSecret, 0, secretLength);
      // Convert the byte array to a string
      String strFromBytes = new String(checkSecret, StandardCharsets.UTF_8);
      if (strFromBytes.equals(SECRET)) {
        this.parseLocalProtocol(bytes);
      } else {
        this.parseExternalProtocol(bytes);
      }
    }
  }

  
  private byte[] addCtrChars(byte[] bytes) {
    byte[] ctrBytes = new byte[] { RETURN, LINE_FEED };
    byte[] tmp = new byte[bytes.length + ctrBytes.length];

    System.arraycopy(bytes, 0, tmp, 0, bytes.length);
    System.arraycopy(ctrBytes, 0, tmp, bytes.length, ctrBytes.length);

    return tmp;
  }

	private byte[] toByteArray(String s){
		byte[] bytes = s.getBytes();
		byte[] ctrBytes = new byte[]{LINE_FEED, RETURN};
		byte[] tmp = new byte[bytes.length + ctrBytes.length];
		
		System.arraycopy(bytes, 0, tmp, 0, bytes.length);
		System.arraycopy(ctrBytes, 0, tmp, bytes.length, ctrBytes.length);
		
		return tmp;		
	}

  private byte[] toByteArray(StatusType status, String key, String value) {
    byte[] secretBytes = SECRET.getBytes();

    byte[] statusBytes = String.format("%-25s", status.name()).getBytes();

    byte[] keyBytes = (key != null) ? key.getBytes() : new byte[0];
    int keyLength = keyBytes.length;

    // Convert value to bytes
    byte[] valueBytes = (value != null) ? value.getBytes() : new byte[0];
    int valueLength = valueBytes.length;

    int totalLength = SECRET.length() + statusBytes.length + 4 + keyLength + 4 + valueLength;

    ByteBuffer buffer = ByteBuffer.allocate(totalLength);
    buffer.put(secretBytes);
    // Append StatusType bytes
    buffer.put(statusBytes);

    // Append key length and key bytes
    if (key != null) {
      buffer.putInt(keyLength);
      buffer.put(keyBytes);
    } else {
      // If value is null, append 0 for length
      buffer.putInt(0);
    }

    // Append value length and value bytes
    if (value != null) {
      buffer.putInt(valueLength);
      buffer.put(valueBytes);
    } else {
      // If value is null, append 0 for length
      buffer.putInt(0);
    }

    return addCtrChars(buffer.array());
  }

  public String toString(){
    return this.msg;
  }
}
