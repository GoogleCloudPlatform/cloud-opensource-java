import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

public class HelloWorldMojoTest
{
    @Test
    public void testExecute() throws MojoExecutionException
    {
        HelloWorldMojo mojo = new HelloWorldMojo();
        mojo.execute();
    }
}