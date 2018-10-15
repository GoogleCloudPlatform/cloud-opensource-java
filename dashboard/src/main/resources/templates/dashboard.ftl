<html lang="en-US">
  <head>
    <title>Google Cloud Platform Code Health Open Source Dashboard</title>
    <link rel="stylesheet" type="text/css" href="dashboard.css">
  </head>
  <body>
    <h1>Google Cloud Platform Dependency Dashboard</h1>
    <h2>Artifact Details</h2>
    
    <#macro testResult row name>
      <#if row.getResult(name)?? ><#-- checking isNotNull() -->
        <#-- When it's not null, the test ran. It's either PASS or FAIL -->
        <#assign test_label = row.getResult(name)?then('PASS', 'FAIL')>
        <#assign failure_count = row.getFailureCount(name)>
      <#else>
        <#-- Null means there's an exception and test couldn't run -->
        <#assign test_label = "UNAVAILABLE">
      </#if>
      <td class='${test_label}' title="${row.getExceptionMessage()!""}">
        <#if row.getResult(name)?? >
          <#if failure_count == 1>1 FAILURE
          <#elseif failure_count gt 1>${failure_count} FAILURES
          <#else>PASS
          </#if>
        <#else>UNAVAILABLE
        </#if>
      </td>
    </#macro>
    
    <table>
      <tr>
        <th>Artifact</th><th>Upper Bounds</th><th>Dependency Convergence</th><th>Global Upper Bounds</th>
      </tr>
      <#list table as row>
        <tr>
          <td><a href='${row.getCoordinates()?replace(":", "_")}.html'>${row.getCoordinates()}</a></td>
          <@testResult row=row name="Upper Bounds"/>
          <@testResult row=row name="Global Upper Bounds"/>
          <@testResult row=row name="Dependency Convergence"/>
        </tr>
      </#list>
    </table>
      
     <h2>Recommended Versions</h2>
      
     <p>These are the most recent versions of dependencies used by any of the covered artifacts.</p> 
      
     <ul>
       <#list latestArtifacts as artifact, version>
         <li>${artifact}:${version}</li>
       </#list>
     </ul>
      
      <hr />
      <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>