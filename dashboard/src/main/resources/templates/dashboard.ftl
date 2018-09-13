<html>
  <head>
    <title>Google Cloud Platform Code Health Open Source Dashboard</title>
  </head>
  <body>
    <h1>Google Cloud Platform Code Health Open Source Dashboard</h1>
    <h2>Projects</h2>
      <ul>
      <#list artifacts as artifact>
        <li><a href='${artifact?replace(":", "_")}.html'>${artifact}</a></li>
      </#list>
      </ul>
      <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>