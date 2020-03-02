package com.google.cloud.tools.opensource.classpath;

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.cloud.tools.opensource.classpath.ExclusionFileParser.LinkageCheckerFilter;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class ExclusionFileParserTest {

  @Test
  public void testParse() throws URISyntaxException, IOException {
    Path exclusionFile = absolutePathOfResource(
        "exclusion-sample-rules/source-only.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    boolean result = matchers.get(0).sourceMatcher.classNameMatcher.match(null ,
        new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testOutput() throws URISyntaxException, IOException {
    XmlMapper mapper = new XmlMapper();

    LinkageCheckerFilter filter = new LinkageCheckerFilter();
    LinkageErrorSourceMatcher sourceMatcher = new LinkageErrorSourceMatcher();
    sourceMatcher.setClassNameMatcher(new LinkageErrorClassNameMatcher("com.google.Foo"));
    LinkageErrorMatcher linkageErrorMatcher1 = new LinkageErrorMatcher();
    linkageErrorMatcher1.sourceMatcher = sourceMatcher;
    filter.matchers.add(linkageErrorMatcher1);

    LinkageErrorSourceMatcher targetMatcher = new LinkageErrorSourceMatcher();
    LinkageErrorMatcher linkageErrorMatcher2 = new LinkageErrorMatcher();
    linkageErrorMatcher2.sourceMatcher = targetMatcher;
    filter.matchers.add(linkageErrorMatcher2);

    mapper.writeValue(System.out, filter);
  }
}
