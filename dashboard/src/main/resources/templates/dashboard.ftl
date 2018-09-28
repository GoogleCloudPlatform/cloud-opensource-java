<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Health Open Source Dashboard</title>
    <style>
    .PASS {
      background-color: green;
      font-weight: bold;
    }
    .FAIL {
      background-color: red;
      font-weight: bold;
    }
    </style>
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
          <td class='${row[1]}'>${row[1]}</td>
          <td class='${row[2]}'>${row[2]}</td>
        </tr>
      </#list>
      </table>
      <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>