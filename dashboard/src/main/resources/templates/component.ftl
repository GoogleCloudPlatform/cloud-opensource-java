<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Health Open Source Report for ${groupId}:${artifactId}:${version}</title>
  </head>
  <body>
    <h1>Google Cloud Platform Code Health Open Source for ${groupId}:${artifactId}:${version}</h1>
    
    <h2>Upper Bounds Check</h2>
    
    <p>Work in Progress</p>
    
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