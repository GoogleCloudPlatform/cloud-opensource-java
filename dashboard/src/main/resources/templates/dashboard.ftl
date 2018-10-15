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
    .UNAVAILABLE {
      background-color: gray;
      font-weight: bold;
    }
    
    body {
      font-family: "Poppins", sans-serif;
      font-weight: 400;
      font-size: 16px;
      line-height: 1.625;
    }

    h1,
    h2,
    h3 {
      color: #333333;
      font-weight: 700;
      margin: 0;
      line-height: 1.2;
    }
    
    h1 {
      font-size: 36pt;
    }
    
    h2 {
      font-size: 30pt;
    }
    
    h3 {
      font-size: 24pt;
    }
    th, td {
      padding: 5pt;
    }
    </style>
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
        <th>Artifact</th>
        <th title="For each transitive dependency the library pulls in, the highest version 
       found anywhere in the dependency tree is picked.">Upper Bounds</th>
        <th title="There is exactly one version of each dependency in the library's transitive dependency tree.
       That is, two artifacts with the same group ID and artifact ID but different versions
       do not appear in the tree. No dependency mediation is necessary.">Dependency Convergence</th>
        <th title="For each transitive dependency the library pulls in, the highest version 
       found anywhere in the union of the BOM's dependency trees is picked.">Global Upper Bounds</th>
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