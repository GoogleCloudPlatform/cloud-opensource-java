package com.google.cloud.tools.opensource.classpath;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class InaccessibleClassProblemTest {
	
	private ClassFile file;
	private Symbol symbol;
	
	@Before
	public void setUp() {
      Path path = Paths.get("/usr/tmp");
      ClassPathEntry entry = new ClassPathEntry(path);
      file = new ClassFile(entry, "foo");
      symbol = new Symbol("") {
        @Override
        public boolean equals(Object other) {
          return false;
        }

        @Override
        public int hashCode() {
          return 0;
        }
      };
	}

    @Test
    public void testProtected() {
      InaccessibleClassProblem problem =
          new InaccessibleClassProblem(file, file, symbol, AccessModifier.PROTECTED);
      Assert.assertEquals("Class foo is protected and referenced by foo", problem.toString());
    }
    
    @Test
    public void testPrivate() {
      InaccessibleClassProblem problem =
          new InaccessibleClassProblem(file, file, symbol, AccessModifier.PRIVATE);
      Assert.assertEquals("Class foo is private and referenced by foo", problem.toString());
    }

    @Test
    public void testPublic() {
      InaccessibleClassProblem problem =
          new InaccessibleClassProblem(file, file, symbol, AccessModifier.PUBLIC);
      Assert.assertEquals("Class foo is public and referenced by foo",
          problem.toString());
    }
    
    @Test
    public void testDefault() {
      InaccessibleClassProblem problem =
          new InaccessibleClassProblem(file, file, symbol, AccessModifier.DEFAULT);
      Assert.assertEquals("Class foo has default access and referenced by foo (different package)",
          problem.toString());
    }

}
