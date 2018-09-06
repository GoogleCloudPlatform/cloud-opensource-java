<html>
  <head>
    <title>Google Cloud Platform Code Health Open Source Report for ${groupId}:${artifactId}:${version}</title>
  </head>
  <body>
    <h1>Google Cloud Platform Code Health Open Source for ${groupId}:${artifactId}:${version}</h1>
    <h2>Suggested Dependency Updates</h2>
    <ul>
      <#list updates as update>
        <li>${update}</li>
      </#list>
    </ul>
  </body>
</html>