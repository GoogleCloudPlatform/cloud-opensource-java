def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

// protobuf-java 3.12.4 has wrong reference to ByteBuffer's methods that are unavailable in Java 8.
// In this message below, the expectation is wrong.
assert buildLog.contains('''\
java.nio.ByteBuffer's method position(int) is expected to return java.nio.ByteBuffer\
 but instead returns java.nio.Buffer;
  referenced by 8 class files
    com.google.protobuf.AllocatedBuffer (com.google.protobuf:protobuf-java:3.12.4)
    com.google.protobuf.BinaryWriter (com.google.protobuf:protobuf-java:3.12.4)
    com.google.protobuf.ByteBufferWriter (com.google.protobuf:protobuf-java:3.12.4)
    com.google.protobuf.CodedInputStream (com.google.protobuf:protobuf-java:3.12.4)
    com.google.protobuf.CodedOutputStream (com.google.protobuf:protobuf-java:3.12.4)
    com.google.protobuf.IterableByteBufferInputStream (com.google.protobuf:protobuf-java:3.12.4)
    com.google.protobuf.NioByteString (com.google.protobuf:protobuf-java:3.12.4)
    com.google.protobuf.Utf8 (com.google.protobuf:protobuf-java:3.12.4)''')