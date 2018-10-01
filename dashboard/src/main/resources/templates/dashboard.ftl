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
          <td><a href='${row.getCoordinates()?replace(":", "_")}.html'>${row.getCoordinates()}</a></td>   
          <td class='${row.getResult("Upper Bounds")?string('PASS', 'FAIL')}'>${row.getResult("Upper Bounds")?string('PASS', 'FAIL')}</td>
          <td class='${row.getResult("Dependency Convergence")?string('PASS', 'FAIL')}'>${row.getResult("Dependency Convergence")?string('PASS', 'FAIL')}</td>
        </tr>
      </#list>
      </table>
      <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>