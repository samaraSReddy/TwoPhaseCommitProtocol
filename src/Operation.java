
public enum Operation {

	READ("read"),
	WRITE("write");
	
	 private String value;

    Operation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
    public static Operation fromString(String text) {
        if (text != null) {
          for (Operation b : Operation.values()) {
            if (text.equalsIgnoreCase(b.value)) {
              return b;
            }
          }
        }
        return null;
      }
    
}
