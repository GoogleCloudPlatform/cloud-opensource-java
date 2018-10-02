<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Health Open Source Report for ${groupId}:${artifactId}:${version}</title>
  </head>
  <body>
    <h1>Google Cloud Platform Code Health Open Source for ${groupId}:${artifactId}:${version}</h1>
    
    <h2>Upper Bounds Check</h2>
    
   <#if upperBoundFailures?size gt 0>
      <h3>Upper Bounds Fixes</h3>
      <ul>
        <#list upperBoundFailures as lower, upper>
          <li>Upgrade ${lower} to ${upper}:
          
          <p>Add this dependency element to the pom.xml for ${groupId}:${artifactId}:${version}:</p>
          
<pre><code>&lt;dependency>
  &lt;groupId>${upper.getGroupId()}&lt;/groupId>
  &lt;artifactId>${upper.getArtifactId()}&lt;/artifactId>
  &lt;version>${upper.getVersion()}&lt;/version>
&lt;/dependency></code></pre>
          
          </li>
        </#list>
      </ul>
      
      <p>If the pom.xml already includes a dependency on ${groupId}:${artifactId}, update the version
         on the existing <code>dependency</code> element. Otherwise add these <code>dependency</code>
         elements to the <code>dependencyManagement</code> section.</p>
      
    <#else>
      <h3 style="color: green">
        ${groupId}:${artifactId}:${version} selects the highest version of all dependencies.
      </h3>
    </#if>
        
    <h2>Dependency Convergence</h2>
    
    <#if updates?size gt 0>
      <h3>Suggested Dependency Updates</h3>
      <ul>
        <#list updates as update>
          <li>${update}</li>
        </#list>
      </ul>
    <#else>
      <h3 style="color: green">${groupId}:${artifactId}:${version} Converges</h3>
    </#if>
    
  </body>
</html>