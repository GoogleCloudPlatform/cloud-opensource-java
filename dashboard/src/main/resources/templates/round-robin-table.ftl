<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Round-robin Table</title>
    <link rel="stylesheet" type="text/css" href="dashboard.css" />
  </head>
  <body>
    <h1>Round-robin Table</h1>
    <table>
      <th>
        <#list artifactList as artifactColumn>
          <td>artifactColumn</td>
        </#list>
      </th>
      <#list artifactList as artifactRow>
        <tr>
        <#list artifactList as artifactColumn>
          <td>${artifactRow}:${artifactColumn}</td>
        </#list>
        </tr>
      </#list>
    </table>

    <hr />
    <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>