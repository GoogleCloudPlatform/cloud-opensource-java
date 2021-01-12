package com.google.cloud.tools.opensource.dependencies;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.Assert;
import org.junit.Test;

public class VerTest {

  @Test
  public void testVersion() {
    ComparableVersion sts = new ComparableVersion("1.38.0");
    ComparableVersion lts = new ComparableVersion("1.38.0.1");
    Assert.assertTrue(sts.compareTo(lts) < 0);
    Assert.assertTrue(lts.compareTo(new ComparableVersion("1.38.0.2")) < 0);
    Assert.assertTrue(new ComparableVersion("1.38.0.1").compareTo(new ComparableVersion("1.39.0")) < 0);
  }

}
