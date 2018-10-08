package com.google.cloud.tools.opensource.classpath;

import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.junit.Assert;
import org.junit.Test;

public class JarDumperTest {
  private final String EXAMPLE_JAR_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar";
  private final String EXAMPLE_CLASS_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";

  @Test
  public void testListExternalMethodReferences() throws IOException {
    URL jarFileUrl = JarDumperTest.class.getClassLoader().getResource(EXAMPLE_JAR_FILE);
    List<FullyQualifiedMethodSignature> signatures = JarDumper.listExternalMethodReferences(
        jarFileUrl);
    Truth.assertThat(signatures).hasSize(28);
    FullyQualifiedMethodSignature expectedExternalMethodReference =
        new FullyQualifiedMethodSignature(
            "io.grpc.protobuf.ProtoUtils",
            "marshaller",
            "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;");
    Truth.assertThat(signatures).contains(expectedExternalMethodReference);

    String classNameInJar = "com.google.firestore.v1beta1.FirestoreGrpc";
    for (FullyQualifiedMethodSignature methodReference : signatures) {
      Truth.assertThat(methodReference.getClassName()).doesNotContain(classNameInJar);
    }
  }

  @Test
  public void testListInnerClasses() throws IOException {
    InputStream classFileInputStream = URLClassLoader.getSystemResourceAsStream(EXAMPLE_CLASS_FILE);
    ClassParser parser = new ClassParser(classFileInputStream, EXAMPLE_CLASS_FILE);
    JavaClass javaClass = parser.parse();

    Set<String> innerClassNames = JarDumper.listInnerClassNames(javaClass);
    Truth.assertThat(innerClassNames).containsExactly(
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFutureStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreMethodDescriptorSupplier",
        "com.google.firestore.v1beta1.FirestoreGrpc$1",
        "io.grpc.MethodDescriptor$Builder",
        "com.google.firestore.v1beta1.FirestoreGrpc$MethodHandlers",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreBaseDescriptorSupplier",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreBlockingStub",
        "io.grpc.ServiceDescriptor$Builder",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreImplBase",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFileDescriptorSupplier",
        "io.grpc.MethodDescriptor$MethodType",
        "io.grpc.MethodDescriptor$Marshaller"
    );
  }
}
