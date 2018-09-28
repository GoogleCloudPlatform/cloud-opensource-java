<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Health Open Source Dashboard</title>
  </head>
  <body>
    <h1>Google Cloud Platform Code Health Open Source Dashboard</h1>
    <h2>Projects</h2>
      <table>
      <tr>
        <th>Artifact</th><th>Upper Bounds</th><th>Dependency Convergence</th>
      </tr>
      <#list table as row>
        <tr>
          <td><a href='${row[0]?replace(":", "_")}.html'>${row[0]}</a></td>
          <td>${row[1]}</td>
          <td>${row[2]}</td>
        </tr>
      </#list>
      </table>
      <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>