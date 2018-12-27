/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.opensource.classpath;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClassDumperTest {
  private static final String EXAMPLE_JAR_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar";

  // We're sure that FirestoreGrpc class comes from this class file because
  // this project (cloud-opensource-java) doesn't have dependency for Cloud Firestore
  private static final String EXAMPLE_CLASS_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";

  private static final Correspondence<SymbolReference, String> SYMBOL_REFERENCE_TARGET_CLASS_NAME =
      new Correspondence<SymbolReference, String>() {
        @Override
        public boolean compare(SymbolReference actual, String expected) {
          return actual.getTargetClassName().equals(expected);
        }

        @Override
        public String toString() {
          return "has target class name equal to";
        }
      };

  private InputStream classFileInputStream;

  @Before
  public void setup() {
    classFileInputStream = URLClassLoader.getSystemResourceAsStream(EXAMPLE_CLASS_FILE);
  }

  @After
  public void cleanup() throws IOException {
    classFileInputStream.close();
  }

  @Test
  public void testMethodDescriptorToClass_byteArray() throws IOException, ClassNotFoundException {
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of());
    Class<?>[] byteArrayClass =
        classDumper.methodDescriptorToClass("([B)Ljava/lang/String;");
    Assert.assertTrue(byteArrayClass[0].isArray());
  }

  @Test
  public void testMethodDescriptorToClass_primitiveTypes()
      throws IOException, ClassNotFoundException {
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of());
    // List of primitive types that appear in descriptor:
    // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3
    Class<?>[] types =
        classDumper.methodDescriptorToClass("(BCDFIJSZ)Ljava/lang/String;");
    Truth.assertThat(types)
        .asList()
        .containsExactly(
            byte.class,
            char.class,
            double.class,
            float.class,
            int.class,
            long.class,
            short.class,
            boolean.class)
        .inOrder();
  }

  @Test
  public void testListInnerClasses() throws IOException {
    InputStream classFileInputStream = URLClassLoader.getSystemResourceAsStream(
        EXAMPLE_CLASS_FILE);
    ClassParser parser = new ClassParser(classFileInputStream, EXAMPLE_CLASS_FILE);
    JavaClass javaClass = parser.parse();

    Set<String> innerClassNames = ClassDumper.listInnerClassNames(javaClass);
    Truth.assertThat(innerClassNames).containsExactly(
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFutureStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreMethodDescriptorSupplier",
        "com.google.firestore.v1beta1.FirestoreGrpc$1",
        "com.google.firestore.v1beta1.FirestoreGrpc$MethodHandlers",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreBaseDescriptorSupplier",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreBlockingStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreImplBase",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFileDescriptorSupplier"
    );
  }

  @Test
  public void testCreationInvalidInput() throws IOException {
    try {
      ClassDumper.create(ImmutableList.of(Paths.get("")));
      Assert.fail("Empty path should generate IOException");
    } catch (IOException ex) {
      // pass
    }
  }

  @Test
  public void testScanSymbolTableFromJar()
      throws URISyntaxException, IOException {
    URL jarFileUrl = URLClassLoader.getSystemResource(EXAMPLE_JAR_FILE);

    SymbolReferenceSet symbolReferenceSet =
        ClassDumper.scanSymbolReferencesInJar(
            Paths.get(jarFileUrl.toURI()));

    Set<FieldSymbolReference> actualFieldReferences = symbolReferenceSet.getFieldReferences();
    FieldSymbolReference expectedFieldReference =
        FieldSymbolReference.builder().setFieldName("BIDI_STREAMING")
            .setSourceClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setTargetClassName("io.grpc.MethodDescriptor$MethodType").build();
    Truth.assertThat(actualFieldReferences).contains(expectedFieldReference);

    Set<MethodSymbolReference> actualMethodReferences = symbolReferenceSet.getMethodReferences();
    MethodSymbolReference expectedMethodReference =
        MethodSymbolReference.builder()
            .setTargetClassName("io.grpc.protobuf.ProtoUtils")
            .setMethodName("marshaller")
            .setSourceClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setDescriptor("(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;")
            .build();
    Truth.assertThat(actualMethodReferences).contains(expectedMethodReference);

    Set<ClassSymbolReference> actualClassReferences = symbolReferenceSet.getClassReferences();
    Truth.assertThat(actualClassReferences).isNotEmpty();
    Truth.assertWithMessage("Class reference should have binary names defined in JLS 13.1")
        .that(actualClassReferences)
        .contains(
            ClassSymbolReference.builder()
                .setSourceClassName("com.google.firestore.v1beta1.FirestoreGrpc")
                .setTargetClassName(
                    "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreMethodDescriptorSupplier")
                .build());
  }

  @Test
  public void testScanSymbolTableFromJar_shouldNotPickArrayClass()
      throws URISyntaxException, IOException {
    URL jarFileUrl = URLClassLoader.getSystemResource("testdata/gax-1.32.0.jar");

    SymbolReferenceSet symbolReferenceSet =
        ClassDumper.scanSymbolReferencesInJar(Paths.get(jarFileUrl.toURI()));

    Set<ClassSymbolReference> actualClassReferences = symbolReferenceSet.getClassReferences();
    Truth.assertThat(actualClassReferences).isNotEmpty();
    Truth.assertWithMessage("Class references should not include array class")
        .that(actualClassReferences)
        .comparingElementsUsing(SYMBOL_REFERENCE_TARGET_CLASS_NAME)
        .doesNotContain("[Ljava.lang.Object;");
  }

  @Test
  public void testClassesInSamePackage() {
    Truth.assertThat(ClassDumper.classesInSamePackage("foo.Abc", "bar.Abc")).isFalse();
    Truth.assertThat(ClassDumper.classesInSamePackage("foo.bar.Abc", "foo.bar.Cde")).isTrue();
    Truth.assertThat(ClassDumper.classesInSamePackage("foo.bar.Abc$XYZ", "foo.bar.Cde")).isTrue();
    Truth.assertThat(ClassDumper.classesInSamePackage("Abc", "Cde")).isTrue();
    Truth.assertThat(ClassDumper.classesInSamePackage("Abc", "xyz.Cde")).isFalse();
  }

  @Test
  public void testEnclosingClassNames() {
    String actualName = ClassDumper.enclosingClassName("com.google.Foo$Bar$Baz");
    Truth.assertThat(actualName).isEqualTo("com.google.Foo$Bar");
    String topLevelClass = ClassDumper.enclosingClassName("com.google.Foo");
    Truth.assertThat(topLevelClass).isNull();
  }

  /*
  @Test
  public void testJarFilesToDefinedClasses_classWithDollars()
      throws IOException, RepositoryException {
    Artifact grpcArtifact = new DefaultArtifact("com.google.code.gson:gson:2.6.2");
    List<Path> paths = StaticLinkageChecker.artifactsToClasspath(ImmutableList.of(grpcArtifact));
    Path gsonJar = paths.get(0);

    ImmutableSetMultimap<Path, String> pathToClasses = ClassDumper
        .jarFilesToDefinedClasses(paths.subList(0, 1));
    ImmutableSet<String> classesInGsonJar = pathToClasses.get(gsonJar);
    Truth.assertThat(classesInGsonJar).contains("com.google.gson.internal.$Gson$Preconditions");
  } */
}
