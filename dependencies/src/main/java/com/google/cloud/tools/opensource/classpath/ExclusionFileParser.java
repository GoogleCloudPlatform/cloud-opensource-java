package com.google.cloud.tools.opensource.classpath;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class ExclusionFileParser {

  static ImmutableList<LinkageErrorMatcher> parse(Path exclusionFile) throws IOException {

    XmlMapper mapper = new XmlMapper();
    LinkageCheckerFilter linkageCheckerFilter = mapper
        .readValue(exclusionFile.toFile(), LinkageCheckerFilter.class);
    return ImmutableList.copyOf(linkageCheckerFilter.matchers);
  }

  static class LinkageCheckerFilter {
    @JacksonXmlProperty(localName = "LinkageError")
    @JacksonXmlElementWrapper(useWrapping = false)
    public final List<LinkageErrorMatcher> matchers = new ArrayList<>();
  }

}
