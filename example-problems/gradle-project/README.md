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
...
Class org.slf4j.helpers.MessageFormatter is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.util.internal.logging.LocationAwareSlf4JLogger (io.grpc:grpc-netty-shaded:1.28.1)
Class io.grpc.internal.BaseDnsNameResolverProvider is not found;
  referenced by 1 class file
    io.grpc.grpclb.SecretGrpclbNameResolverProvider (io.grpc:grpc-grpclb:1.28.1)
Class org.apache.avalon.framework.logger.Logger is not found;
  referenced by 1 class file
    org.apache.commons.logging.impl.AvalonLogger (commons-logging:commons-logging:1.2)
...

> Task :linkageCheck FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':linkageCheck'.
> Linakge Checker found errors in one of configurations. See above for the details.

...

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
