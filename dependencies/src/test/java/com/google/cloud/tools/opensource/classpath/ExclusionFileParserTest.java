package com.google.cloud.tools.opensource.classpath;

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.Test;

public class ExclusionFileParserTest {

  @Test
  public void testParse() throws URISyntaxException {
    Path exclusionFile = absolutePathOfResource(
        "exclusion-sample-rules/source-only.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
  }
}
