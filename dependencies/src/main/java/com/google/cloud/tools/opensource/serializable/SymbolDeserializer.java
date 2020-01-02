package com.google.cloud.tools.opensource.serializable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.IntNode;
import com.google.cloud.tools.opensource.classpath.ClassSymbol;
import com.google.cloud.tools.opensource.classpath.FieldSymbol;
import com.google.cloud.tools.opensource.classpath.MethodSymbol;
import java.io.IOException;

public class SymbolDeserializer extends StdDeserializer {

  public SymbolDeserializer() {
    this(null);
  }

  public SymbolDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public Object deserialize(JsonParser parser, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {

    JsonNode node = parser.getCodec().readTree(parser);

    if (!node.has("className")) {
      throw new IOException("No class name field");
    }

    String className = node.get("className").asText();
    if (node.has("descriptor")) {
      String descriptor = node.get("descriptor").asText();
      String name = node.get("name").asText();
      if (node.has("isInterfaceMethod")) {
        return new MethodSymbol(className, name, descriptor, false);
      }
      return new FieldSymbol(className, name, descriptor);
    }
    return new ClassSymbol(className);
  }
}
