<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Round-robin Table</title>
    <link rel="stylesheet" type="text/css" href="dashboard.css" />
  </head>
  <body>
    <h1>Static Linkage Check: BOM Round-robin Table</h1>
    <table class='round-robin-table'>
      <tr>
        <th></th>
        <#list artifactList as artifactColumn>
        <th>${artifactColumn}</th>
        </#list>
      </tr>
      <#list artifactList as artifactRow>
        <tr>
          <th>${artifactRow}</th>
        <#list artifactList as artifactColumn>
          <td class='round-robin-table-cell'>
          <#if artifactRow == artifactColumn>
            <#assign linkageCheckResult=table[artifactRow]>
            <span title='${artifactRow}'>${linkageCheckResult?size}</span>
          <#else>
            <#assign key=artifactColumn+","+artifactRow>
            <#assign linkageCheckResult=table[key]>
            <span title='${artifactRow}:${artifactColumn}'>${linkageCheckResult?size}</span>
          </#if>
          </td>
        </#list>
        </tr>
      </#list>
    </table>

    <hr />
    <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>