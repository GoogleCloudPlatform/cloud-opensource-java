# Linakge Checker Gradle Plugin Example Project

The Linkage Checker Gradle plugin installs "linkageCheck" task to the project.
This task finds the missing classes and discrepancy between gax and gRPC versions.

Example invocation:

```
$ pwd
/Users/suztomo/cloud-opensource-java/example-problems/gradle-project
$ ../../gradle-plugin/gradlew linkageCheck

> Task :linkageCheck
Configurations [configuration ':compile']
compile: Linkage Checker rule found 58 errors. Linkage error report:
Class com.jcraft.jzlib.JZlib is not found;
  referenced by 4 class files
    io.grpc.netty.shaded.io.netty.handler.codec.spdy.SpdyHeaderBlockJZlibEncoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.JZlibEncoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.ZlibUtil (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.JZlibDecoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.jcraft.jzlib.Deflater is not found;
  referenced by 3 class files
    io.grpc.netty.shaded.io.netty.handler.codec.spdy.SpdyHeaderBlockJZlibEncoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.JZlibEncoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.ZlibUtil (io.grpc:grpc-netty-shaded:1.28.1)
Class org.eclipse.jetty.alpn.ALPN is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.JettyAlpnSslEngine (io.grpc:grpc-netty-shaded:1.28.1)
Class org.eclipse.jetty.npn.NextProtoNego is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.JettyNpnSslEngine (io.grpc:grpc-netty-shaded:1.28.1)
Class org.eclipse.jetty.npn.NextProtoNego$ServerProvider is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.JettyNpnSslEngine (io.grpc:grpc-netty-shaded:1.28.1)
Class org.bouncycastle.asn1.x500.X500Name is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleSelfSignedCertGenerator (io.grpc:grpc-netty-shaded:1.28.1)
Class org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleSelfSignedCertGenerator (io.grpc:grpc-netty-shaded:1.28.1)
Class org.bouncycastle.operator.jcajce.JcaContentSignerBuilder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleSelfSignedCertGenerator (io.grpc:grpc-netty-shaded:1.28.1)
Class org.bouncycastle.cert.X509v3CertificateBuilder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleSelfSignedCertGenerator (io.grpc:grpc-netty-shaded:1.28.1)
Class org.bouncycastle.cert.jcajce.JcaX509CertificateConverter is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleSelfSignedCertGenerator (io.grpc:grpc-netty-shaded:1.28.1)
Class org.bouncycastle.jce.provider.BouncyCastleProvider is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleSelfSignedCertGenerator (io.grpc:grpc-netty-shaded:1.28.1)
Class org.eclipse.jetty.npn.NextProtoNego$ClientProvider is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.JettyNpnSslEngine (io.grpc:grpc-netty-shaded:1.28.1)
Class org.eclipse.jetty.alpn.ALPN$ServerProvider is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.JettyAlpnSslEngine (io.grpc:grpc-netty-shaded:1.28.1)
Class org.eclipse.jetty.alpn.ALPN$ClientProvider is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.JettyAlpnSslEngine (io.grpc:grpc-netty-shaded:1.28.1)
Class lzma.sdk.lzma.Encoder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzmaFrameEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.ning.compress.lzf.util.ChunkEncoderFactory is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzfEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.ning.compress.lzf.ChunkEncoder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzfEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.ning.compress.BufferRecycler is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzfEncoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzfDecoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.ning.compress.lzf.LZFEncoder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzfEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.jcraft.jzlib.JZlib$WrapperType is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.ZlibUtil (io.grpc:grpc-netty-shaded:1.28.1)
Class com.jcraft.jzlib.Inflater is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.handler.codec.compression.ZlibUtil (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.JZlibDecoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.google.protobuf.nano.MessageNano is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.handler.codec.protobuf.ProtobufEncoderNano (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.protobuf.ProtobufDecoderNano (io.grpc:grpc-netty-shaded:1.28.1)
Class com.google.protobuf.nano.CodedOutputByteBufferNano is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.protobuf.ProtobufEncoderNano (io.grpc:grpc-netty-shaded:1.28.1)
Class org.jboss.marshalling.ByteOutput is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.ChannelBufferByteOutput (io.grpc:grpc-netty-shaded:1.28.1)
Class org.jboss.marshalling.Unmarshaller is not found;
  referenced by 4 class files
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.ThreadLocalUnmarshallerProvider (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.ContextBoundUnmarshallerProvider (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.CompatibleMarshallingDecoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.MarshallingDecoder (io.grpc:grpc-netty-shaded:1.28.1)
Class org.jboss.marshalling.MarshallerFactory is not found;
  referenced by 4 class files
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.ThreadLocalUnmarshallerProvider (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.DefaultMarshallerProvider (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.ThreadLocalMarshallerProvider (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.DefaultUnmarshallerProvider (io.grpc:grpc-netty-shaded:1.28.1)
Class org.jboss.marshalling.Marshaller is not found;
  referenced by 3 class files
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.ThreadLocalMarshallerProvider (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.CompatibleMarshallingEncoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.MarshallingEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class org.jboss.marshalling.ByteInput is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.LimitingByteInput (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.marshalling.ChannelBufferByteInput (io.grpc:grpc-netty-shaded:1.28.1)
Class net.jpountz.xxhash.XXHash32 is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4XXHash32 (io.grpc:grpc-netty-shaded:1.28.1)
Class net.jpountz.xxhash.XXHashFactory is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4XXHash32 (io.grpc:grpc-netty-shaded:1.28.1)
Class net.jpountz.lz4.LZ4Factory is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4FrameDecoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4FrameEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class net.jpountz.lz4.LZ4Exception is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4FrameDecoder (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4FrameEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class net.jpountz.lz4.LZ4FastDecompressor is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4FrameDecoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.ning.compress.lzf.util.ChunkDecoderFactory is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzfDecoder (io.grpc:grpc-netty-shaded:1.28.1)
Class com.ning.compress.lzf.ChunkDecoder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.LzfDecoder (io.grpc:grpc-netty-shaded:1.28.1)
Class net.jpountz.lz4.LZ4Compressor is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.codec.compression.Lz4FrameEncoder (io.grpc:grpc-netty-shaded:1.28.1)
Class reactor.blockhound.integration.BlockHoundIntegration is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.Hidden (io.grpc:grpc-netty-shaded:1.28.1)
Class reactor.blockhound.BlockHound$Builder is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.Hidden (io.grpc:grpc-netty-shaded:1.28.1)
Class org.apache.log4j.Logger is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4JLogger (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4JLoggerFactory (io.grpc:grpc-netty-shaded:1.28.1)
Class org.apache.log4j.Level is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4JLogger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.apache.logging.log4j.Logger is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2Logger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.apache.logging.log4j.LogManager is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2LoggerFactory (io.grpc:grpc-netty-shaded:1.28.1)
Class org.slf4j.LoggerFactory is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLoggerFactory (io.grpc:grpc-netty-shaded:1.28.1)
Class org.slf4j.helpers.NOPLoggerFactory is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLoggerFactory (io.grpc:grpc-netty-shaded:1.28.1)
Class org.slf4j.spi.LocationAwareLogger is not found;
  referenced by 2 class files
    io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLoggerFactory (io.grpc:grpc-netty-shaded:1.28.1)
    io.grpc.netty.shaded.io.netty.util.internal.logging.LocationAwareSlf4JLogger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.apache.logging.log4j.spi.ExtendedLoggerWrapper is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2Logger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.apache.logging.log4j.spi.ExtendedLogger is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2Logger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.apache.logging.log4j.Level is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2Logger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.slf4j.Logger is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLogger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.slf4j.helpers.FormattingTuple is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.LocationAwareSlf4JLogger (io.grpc:grpc-netty-shaded:1.28.1)
Class org.slf4j.helpers.MessageFormatter is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.LocationAwareSlf4JLogger (io.grpc:grpc-netty-shaded:1.28.1)
Class io.grpc.internal.BaseDnsNameResolverProvider is not found;
  referenced by 1 class file
    io.grpc.grpclb.SecretGrpclbNameResolverProvider (io.grpc:grpc-grpclb:1.28.1)
Class org.apache.avalon.framework.logger.Logger is not found;
  referenced by 1 class file
    org.apache.commons.logging.impl.AvalonLogger (commons-logging:commons-logging:1.2)
Class org.apache.log.Hierarchy is not found;
  referenced by 1 class file
    org.apache.commons.logging.impl.LogKitLogger (commons-logging:commons-logging:1.2)
Class org.apache.log.Logger is not found;
  referenced by 1 class file
    org.apache.commons.logging.impl.LogKitLogger (commons-logging:commons-logging:1.2)
(io.grpc:grpc-core:1.29.0) io.grpc.internal.GrpcAttributes's field ATTR_LB_ADDR_AUTHORITY is not found;
  referenced by 2 class files
    io.grpc.alts.internal.AltsProtocolNegotiator (io.grpc:grpc-alts:1.28.1)
    io.grpc.grpclb.GrpclbConstants (io.grpc:grpc-grpclb:1.28.1)
(io.grpc:grpc-core:1.29.0) io.grpc.internal.GrpcAttributes's field ATTR_LB_PROVIDED_BACKEND is not found;
  referenced by 2 class files
    io.grpc.alts.internal.AltsProtocolNegotiator (io.grpc:grpc-alts:1.28.1)
    io.grpc.grpclb.GrpclbState (io.grpc:grpc-grpclb:1.28.1)
(io.grpc:grpc-core:1.29.0) io.grpc.internal.GrpcAttributes's field ATTR_LB_ADDRS is not found;
  referenced by 1 class file
    io.grpc.grpclb.GrpclbConstants (io.grpc:grpc-grpclb:1.28.1)


> Task :linkageCheck FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':linkageCheck'.
> Linakge Checker found errors in one of configurations. See above for the details.

* Try:
Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.

* Get more help at https://help.gradle.org

Deprecated Gradle features were used in this build, making it incompatible with Gradle 7.0.
Use '--warning-mode all' to show the individual deprecation warnings.
See https://docs.gradle.org/6.5/userguide/command_line_interface.html#sec:command_line_warnings

BUILD FAILED in 4s
1 actionable task: 1 executed
```

Among other errors, the error on the missing class `BaseDnsNameResolverProvider` explains
the cause of "BaseDnsNameResolverProvider exception with 1.29?" ([grpc-java#7002](
https://github.com/grpc/grpc-java/issues/7002)).

```
Class io.grpc.internal.BaseDnsNameResolverProvider is not found;
  referenced by 1 class file
    io.grpc.grpclb.SecretGrpclbNameResolverProvider (io.grpc:grpc-grpclb:1.28.1)
```
